//! Account-local product catalog. It never changes Zalo Business entitlements.

use anyhow::{bail, ensure, Context, Result};
use rusqlite::{params, Connection};
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::{Component, Path, PathBuf};

const LIST_LIMIT: i64 = 200;
const IMAGE_DIRECTORY: &str = "product-images";
const MAX_IMAGE_BYTES: u64 = 10 * 1024 * 1024;
const MAX_IMAGE_PIXELS: u64 = 16_000_000;
const MAX_LIBRARY_BYTES: u64 = 200 * 1024 * 1024;

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Product {
    pub id: String,
    pub sku: String,
    pub name: String,
    pub description: String,
    pub price_vnd: i64,
    pub variants: String,
    pub stock_note: String,
    pub image_path: String,
    pub updated_at: i64,
}

/// Versions 3 and 4 extend the account database without changing chat history.
pub(crate) fn init_schema(conn: &Connection) -> Result<()> {
    let version: i64 = conn.query_row("PRAGMA user_version", [], |row| row.get(0))?;
    ensure!(
        version <= 4,
        "Dữ liệu được tạo bởi phiên bản ứng dụng mới hơn"
    );
    if version == 4 {
        return Ok(());
    }
    ensure!(version >= 2, "Cần nâng cấp dữ liệu cá nhân trước danh mục");
    if version < 3 {
        let tx = conn.unchecked_transaction()?;
        tx.execute_batch(
            "CREATE TABLE products (
            id TEXT PRIMARY KEY NOT NULL,
            sku TEXT NOT NULL DEFAULT '',
            name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            price_vnd INTEGER NOT NULL DEFAULT 0 CHECK(price_vnd >= 0),
            variants TEXT NOT NULL DEFAULT '',
            stock_note TEXT NOT NULL DEFAULT '',
            updated_at INTEGER NOT NULL
        );
        CREATE UNIQUE INDEX idx_products_sku ON products(sku COLLATE NOCASE) WHERE sku <> '';
        CREATE INDEX idx_products_updated_at ON products(updated_at DESC);
        PRAGMA user_version = 3;",
        )?;
        tx.commit()?;
    }
    let tx = conn.unchecked_transaction()?;
    tx.execute_batch(
        "ALTER TABLE products ADD COLUMN image_path TEXT NOT NULL DEFAULT '';
         PRAGMA user_version = 4;",
    )?;
    tx.commit()?;
    Ok(())
}

fn clean(value: &str, label: &str, max_chars: usize, multiline: bool) -> Result<String> {
    ensure!(value.chars().count() <= max_chars, "{label} quá dài");
    ensure!(
        !value
            .chars()
            .any(|ch| ch.is_control() && !(multiline && ch == '\n')),
        "{label} chứa ký tự điều khiển"
    );
    Ok(value.trim().to_owned())
}

fn fresh_id() -> Result<String> {
    let mut bytes = [0_u8; 16];
    getrandom::fill(&mut bytes)?;
    Ok(bytes.iter().map(|byte| format!("{byte:02x}")).collect())
}

/// Save an editable product with bounded, parameterized fields.
pub fn save(conn: &Connection, mut product: Product, now_ms: i64) -> Result<Product> {
    product.sku = clean(&product.sku, "Mã sản phẩm", 80, false)?;
    product.name = clean(&product.name, "Tên sản phẩm", 160, false)?;
    product.description = clean(&product.description, "Mô tả", 3000, true)?;
    product.variants = clean(&product.variants, "Biến thể", 500, false)?;
    product.stock_note = clean(&product.stock_note, "Ghi chú tồn kho", 1000, false)?;
    if !product.image_path.is_empty() {
        managed_image_path(Path::new("."), &product.image_path)?;
    }
    ensure!(!product.name.is_empty(), "Nhập tên sản phẩm");
    ensure!(product.price_vnd >= 0, "Giá không được âm");
    ensure!(
        product.id.is_empty()
            || (product.id.len() == 32 && product.id.bytes().all(|b| b.is_ascii_hexdigit())),
        "Mã sản phẩm nội bộ không hợp lệ"
    );
    if product.id.is_empty() {
        product.id = fresh_id()?;
    }
    if !product.sku.is_empty() {
        let duplicate: i64 = conn.query_row(
            "SELECT EXISTS(SELECT 1 FROM products WHERE sku=?1 COLLATE NOCASE AND id<>?2)",
            params![product.sku, product.id],
            |row| row.get(0),
        )?;
        ensure!(duplicate == 0, "Mã SKU này đã tồn tại");
    }
    product.updated_at = now_ms;
    conn.execute(
        "INSERT INTO products(id,sku,name,description,price_vnd,variants,stock_note,updated_at,image_path)
         VALUES(?1,?2,?3,?4,?5,?6,?7,?8,?9)
         ON CONFLICT(id) DO UPDATE SET sku=excluded.sku,name=excluded.name,
         description=excluded.description,price_vnd=excluded.price_vnd,
         variants=excluded.variants,stock_note=excluded.stock_note,updated_at=excluded.updated_at,
         image_path=excluded.image_path",
        params![
            product.id,
            product.sku,
            product.name,
            product.description,
            product.price_vnd,
            product.variants,
            product.stock_note,
            product.updated_at,
            product.image_path
        ],
    )?;
    Ok(product)
}

/// Remove only the selected local product.
pub fn delete(conn: &Connection, id: &str) -> Result<()> {
    ensure!(
        id.len() == 32 && id.bytes().all(|b| b.is_ascii_hexdigit()),
        "Mã sản phẩm nội bộ không hợp lệ"
    );
    conn.execute("DELETE FROM products WHERE id=?1", [id])?;
    Ok(())
}

fn escape_like(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace('%', "\\%")
        .replace('_', "\\_")
}

/// Search only local catalog rows and bound the rendered result set.
pub fn list(conn: &Connection, query: &str) -> Result<Vec<Product>> {
    ensure!(query.chars().count() <= 120, "Từ khóa tìm kiếm quá dài");
    let pattern = format!("%{}%", escape_like(query.trim()));
    let mut stmt = conn.prepare(
        "SELECT id,sku,name,description,price_vnd,variants,stock_note,updated_at,image_path
         FROM products
         WHERE name LIKE ?1 ESCAPE '\\' OR sku LIKE ?1 ESCAPE '\\' OR description LIKE ?1 ESCAPE '\\'
         ORDER BY updated_at DESC, id DESC LIMIT ?2",
    )?;
    let products = stmt
        .query_map(params![pattern, LIST_LIMIT], |row| {
            Ok(Product {
                id: row.get(0)?,
                sku: row.get(1)?,
                name: row.get(2)?,
                description: row.get(3)?,
                price_vnd: row.get(4)?,
                variants: row.get(5)?,
                stock_note: row.get(6)?,
                updated_at: row.get(7)?,
                image_path: row.get(8)?,
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(products)
}

/// Resolve only a content-addressed file in the private product-image directory.
pub fn managed_image_path(root: &Path, relative: &str) -> Result<PathBuf> {
    let path = Path::new(relative);
    ensure!(!path.is_absolute(), "Đường dẫn ảnh sản phẩm không hợp lệ");
    let mut parts = path.components();
    let Some(Component::Normal(directory)) = parts.next() else {
        bail!("Đường dẫn ảnh sản phẩm không hợp lệ");
    };
    let Some(Component::Normal(file)) = parts.next() else {
        bail!("Đường dẫn ảnh sản phẩm không hợp lệ");
    };
    ensure!(
        parts.next().is_none() && directory == IMAGE_DIRECTORY,
        "Đường dẫn ảnh sản phẩm không hợp lệ"
    );
    let filename = file
        .to_str()
        .ok_or_else(|| anyhow::anyhow!("Tên ảnh sản phẩm không hợp lệ"))?;
    let Some((digest, extension)) = filename.rsplit_once('.') else {
        bail!("Tên ảnh sản phẩm không hợp lệ");
    };
    ensure!(
        digest.len() == 64
            && digest.bytes().all(|byte| byte.is_ascii_hexdigit())
            && matches!(extension, "png" | "jpg" | "gif"),
        "Tên ảnh sản phẩm không hợp lệ"
    );
    Ok(root.join(path))
}

fn reject_symlink(path: &Path) -> Result<()> {
    match std::fs::symlink_metadata(path) {
        Ok(metadata) if metadata.file_type().is_symlink() => {
            bail!("Không dùng liên kết tệp cho ảnh sản phẩm")
        }
        Ok(_) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error.into()),
    }
}

fn library_bytes_after_cleanup(conn: &Connection, root: &Path) -> Result<u64> {
    let directory = root.join(IMAGE_DIRECTORY);
    reject_symlink(&directory)?;
    if !directory.exists() {
        return Ok(0);
    }
    let mut total = 0_u64;
    for entry in std::fs::read_dir(&directory)? {
        let entry = entry?;
        let metadata = std::fs::symlink_metadata(entry.path())?;
        ensure!(
            metadata.is_file() && !metadata.file_type().is_symlink(),
            "Thư viện ảnh sản phẩm chứa tệp không hợp lệ"
        );
        let name = entry.file_name();
        let relative = format!("{IMAGE_DIRECTORY}/{}", name.to_string_lossy());
        if managed_image_path(root, &relative).is_ok() {
            let referenced: i64 = conn.query_row(
                "SELECT EXISTS(SELECT 1 FROM products WHERE image_path=?1)",
                [&relative],
                |row| row.get(0),
            )?;
            if referenced == 0 {
                std::fs::remove_file(entry.path())?;
                continue;
            }
        }
        total = total.saturating_add(metadata.len());
    }
    Ok(total)
}

/// Validate, deduplicate and privately copy a local product image with a total library cap.
pub fn import_image(conn: &Connection, root: &Path, source: &Path) -> Result<String> {
    let metadata = std::fs::symlink_metadata(source).context("Không đọc được tệp ảnh")?;
    ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "Hãy chọn một tệp ảnh thường"
    );
    ensure!(
        metadata.len() <= MAX_IMAGE_BYTES,
        "Ảnh sản phẩm vượt 10 MiB"
    );
    let mut bytes = Vec::with_capacity(metadata.len() as usize);
    std::fs::File::open(source)?
        .take(MAX_IMAGE_BYTES + 1)
        .read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_IMAGE_BYTES,
        "Ảnh sản phẩm vượt 10 MiB"
    );
    let reader = image::ImageReader::new(std::io::Cursor::new(&bytes)).with_guessed_format()?;
    let format = reader.format();
    let extension = match format {
        Some(image::ImageFormat::Png) => "png",
        Some(image::ImageFormat::Jpeg) => "jpg",
        Some(image::ImageFormat::Gif) => "gif",
        _ => bail!("Chỉ hỗ trợ ảnh PNG, JPEG hoặc GIF"),
    };
    let (width, height) = reader.into_dimensions().context("Tệp ảnh bị lỗi")?;
    ensure!(
        u64::from(width).saturating_mul(u64::from(height)) <= MAX_IMAGE_PIXELS,
        "Ảnh có quá nhiều điểm ảnh"
    );
    image::load_from_memory_with_format(&bytes, format.expect("supported image format"))
        .context("Không giải mã được ảnh sản phẩm")?;
    let digest = format!("{:x}", Sha256::digest(&bytes));
    let relative = format!("{IMAGE_DIRECTORY}/{digest}.{extension}");
    let destination = managed_image_path(root, &relative)?;
    let directory = root.join(IMAGE_DIRECTORY);
    reject_symlink(&directory)?;
    crate::data_dir::ensure_private_dir(&directory)?;
    reject_symlink(&destination)?;
    let total = library_bytes_after_cleanup(conn, root)?;
    let existing = match std::fs::symlink_metadata(&destination) {
        Ok(metadata) => Some(metadata),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(error) => return Err(error.into()),
    };
    let existing_bytes = if let Some(metadata) = existing.as_ref() {
        ensure!(
            metadata.is_file() && metadata.len() <= MAX_IMAGE_BYTES,
            "Ảnh đã lưu không hợp lệ"
        );
        Some(std::fs::read(&destination)?)
    } else {
        None
    };
    if existing_bytes.as_deref() != Some(bytes.as_slice()) {
        ensure!(
            total
                .saturating_sub(existing.as_ref().map_or(0, |metadata| metadata.len()))
                .saturating_add(bytes.len() as u64)
                <= MAX_LIBRARY_BYTES,
            "Thư viện ảnh sản phẩm đã đầy (200 MiB)"
        );
        crate::data_dir::write_private_atomic(&destination, &bytes)?;
    } else {
        crate::data_dir::ensure_private_file(&destination)?;
    }
    Ok(relative)
}

/// Decode a small first-frame preview on a background worker.
pub fn preview_image(root: &Path, relative: &str) -> Result<image::RgbaImage> {
    let path = managed_image_path(root, relative)?;
    reject_symlink(&root.join(IMAGE_DIRECTORY))?;
    reject_symlink(&path)?;
    let metadata = std::fs::metadata(&path)?;
    ensure!(
        metadata.is_file() && metadata.len() <= MAX_IMAGE_BYTES,
        "Ảnh sản phẩm không hợp lệ"
    );
    let mut bytes = Vec::with_capacity(metadata.len() as usize);
    std::fs::File::open(&path)?
        .take(MAX_IMAGE_BYTES + 1)
        .read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_IMAGE_BYTES,
        "Ảnh sản phẩm không hợp lệ"
    );
    let expected_digest = relative
        .rsplit_once('/')
        .and_then(|(_, filename)| filename.split_once('.'))
        .map(|(digest, _)| digest)
        .ok_or_else(|| anyhow::anyhow!("Tên ảnh sản phẩm không hợp lệ"))?;
    ensure!(
        format!("{:x}", Sha256::digest(&bytes)).eq_ignore_ascii_case(expected_digest),
        "Ảnh sản phẩm đã thay đổi trên đĩa"
    );
    let reader = image::ImageReader::new(std::io::Cursor::new(&bytes)).with_guessed_format()?;
    let (width, height) = reader.into_dimensions()?;
    ensure!(
        u64::from(width).saturating_mul(u64::from(height)) <= MAX_IMAGE_PIXELS,
        "Ảnh có quá nhiều điểm ảnh"
    );
    Ok(image::load_from_memory(&bytes)?
        .thumbnail(width.min(160), height.min(160))
        .to_rgba8())
}

/// Plain text preview inserted into the composer; the user decides whether to send it.
pub fn composer_card(product: &Product) -> String {
    let mut card = product.name.clone();
    if !product.sku.is_empty() {
        card.push_str(&format!("\nMã: {}", product.sku));
    }
    card.push_str(&format!("\nGiá: {} đ", product.price_vnd));
    if !product.variants.is_empty() {
        card.push_str(&format!("\nBiến thể: {}", product.variants));
    }
    if !product.description.is_empty() {
        card.push_str(&format!("\n{}", product.description));
    }
    card
}

#[cfg(test)]
mod tests {
    use super::*;

    fn database() -> crate::store::MessageStore {
        crate::store::MessageStore::open(":memory:").unwrap()
    }

    #[test]
    fn product_crud_and_search_keep_user_text_literal() {
        let store = database();
        let product = save(
            &store.conn,
            Product {
                sku: "A_%".into(),
                name: "Áo mẫu".into(),
                price_vnd: 150_000,
                ..Default::default()
            },
            123,
        )
        .unwrap();
        assert_eq!(list(&store.conn, "A_%").unwrap().len(), 1);
        assert!(list(&store.conn, "Axx").unwrap().is_empty());
        assert_eq!(list(&store.conn, "Áo").unwrap()[0].price_vnd, 150_000);
        let edited = save(
            &store.conn,
            Product {
                name: "Áo mới".into(),
                ..product.clone()
            },
            124,
        )
        .unwrap();
        assert_eq!(edited.id, product.id);
        assert_eq!(list(&store.conn, "mới").unwrap().len(), 1);
        assert!(save(
            &store.conn,
            Product {
                sku: "a_%".into(),
                name: "Trùng SKU".into(),
                ..Default::default()
            },
            125
        )
        .is_err());
        delete(&store.conn, &product.id).unwrap();
        assert!(list(&store.conn, "").unwrap().is_empty());
    }

    #[test]
    fn migration_and_validation_do_not_damage_chat_tables() {
        let store = database();
        init_schema(&store.conn).unwrap();
        assert_eq!(
            store
                .conn
                .query_row("PRAGMA user_version", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            4
        );
        assert!(save(
            &store.conn,
            Product {
                price_vnd: -1,
                name: "Lỗi".into(),
                ..Default::default()
            },
            1
        )
        .is_err());
        assert!(save(
            &store.conn,
            Product {
                name: "bad\nname".into(),
                ..Default::default()
            },
            1
        )
        .is_err());
        assert_eq!(
            store
                .conn
                .query_row("SELECT count(*) FROM messages", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            0
        );
        let other = database();
        assert!(list(&other.conn, "").unwrap().is_empty());
    }

    #[test]
    fn version_two_upgrade_preserves_existing_message() {
        let store = database();
        store.conn.execute_batch("DROP TABLE products; PRAGMA user_version=2; INSERT INTO messages(msg_id,thread_id,sender_id,sender_name,text,msg_type,ts,is_outgoing,raw_json) VALUES('m1','t1','u1','Example','kept','webchat',1,0,'{}');").unwrap();
        init_schema(&store.conn).unwrap();
        assert_eq!(
            store
                .conn
                .query_row("SELECT text FROM messages WHERE msg_id='m1'", [], |r| {
                    r.get::<_, String>(0)
                })
                .unwrap(),
            "kept"
        );
        assert_eq!(
            store
                .conn
                .query_row("PRAGMA user_version", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            4
        );
    }

    #[test]
    fn composer_card_is_only_a_draft_preview() {
        let product = Product {
            name: "Áo thử".into(),
            sku: "SKU1".into(),
            price_vnd: 1000,
            variants: "Đỏ".into(),
            description: "Cỡ M".into(),
            ..Default::default()
        };
        assert_eq!(
            composer_card(&product),
            "Áo thử\nMã: SKU1\nGiá: 1000 đ\nBiến thể: Đỏ\nCỡ M"
        );
    }

    #[test]
    fn catalog_can_store_more_than_two_hundred_and_reopen() {
        let directory = crate::data_dir::PrivateTempDir::new("catalog-reopen-fixture").unwrap();
        let path = directory.path().join("history.db");
        {
            let store = crate::store::MessageStore::open(path.to_str().unwrap()).unwrap();
            for index in 0..205 {
                save(
                    &store.conn,
                    Product {
                        sku: format!("SKU-{index}"),
                        name: format!("Sản phẩm {index}"),
                        ..Default::default()
                    },
                    index,
                )
                .unwrap();
            }
            assert_eq!(list(&store.conn, "").unwrap().len(), 200);
        }
        let reopened = crate::store::MessageStore::open(path.to_str().unwrap()).unwrap();
        assert_eq!(list(&reopened.conn, "SKU-0").unwrap().len(), 1);
        assert_eq!(
            reopened
                .conn
                .query_row("SELECT count(*) FROM products", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            205
        );
    }

    #[test]
    fn newer_schema_is_rejected_before_table_writes() {
        let directory = crate::data_dir::PrivateTempDir::new("catalog-future-schema").unwrap();
        let path = directory.path().join("future.db");
        let conn = Connection::open(&path).unwrap();
        conn.execute_batch("PRAGMA user_version=5;").unwrap();
        drop(conn);
        assert!(crate::store::MessageStore::open(path.to_str().unwrap()).is_err());
    }

    #[test]
    fn version_three_upgrade_keeps_product_and_adds_image_slot() {
        let store = database();
        store.conn.execute_batch("ALTER TABLE products DROP COLUMN image_path; PRAGMA user_version=3; INSERT INTO products(id,sku,name,description,price_vnd,variants,stock_note,updated_at) VALUES('0123456789abcdef0123456789abcdef','A','Old','',5,'','',1);").unwrap();
        init_schema(&store.conn).unwrap();
        let products = list(&store.conn, "Old").unwrap();
        assert_eq!(products[0].price_vnd, 5);
        assert!(products[0].image_path.is_empty());
        assert_eq!(
            store
                .conn
                .query_row("PRAGMA user_version", [], |r| r.get::<_, i64>(0))
                .unwrap(),
            4
        );
    }

    #[test]
    fn image_import_deduplicates_and_preview_is_bounded() {
        let store = database();
        let directory = crate::data_dir::PrivateTempDir::new("catalog-image-fixture").unwrap();
        let source = directory.path().join("source.png");
        image::DynamicImage::ImageRgba8(image::RgbaImage::from_pixel(
            2,
            2,
            image::Rgba([255, 0, 0, 255]),
        ))
        .save(&source)
        .unwrap();
        let first = import_image(&store.conn, directory.path(), &source).unwrap();
        assert_eq!(
            preview_image(directory.path(), &first)
                .unwrap()
                .dimensions(),
            (2, 2)
        );
        assert_eq!(
            import_image(&store.conn, directory.path(), &source).unwrap(),
            first
        );
        let mut product = Product {
            name: "Ảnh thử".into(),
            image_path: first.clone(),
            ..Default::default()
        };
        product = save(&store.conn, product, 1).unwrap();
        assert_eq!(list(&store.conn, "Ảnh thử").unwrap()[0].image_path, first);
        assert!(managed_image_path(directory.path(), "../session.json").is_err());
        assert!(managed_image_path(directory.path(), "product-images/other.png").is_err());
        let oversized = directory.path().join("oversized.gif");
        std::fs::File::create(&oversized)
            .unwrap()
            .set_len(MAX_IMAGE_BYTES + 1)
            .unwrap();
        assert!(import_image(&store.conn, directory.path(), &oversized).is_err());
        let managed = managed_image_path(directory.path(), &product.image_path).unwrap();
        std::fs::write(&managed, b"tampered").unwrap();
        assert!(preview_image(directory.path(), &product.image_path).is_err());
    }
}

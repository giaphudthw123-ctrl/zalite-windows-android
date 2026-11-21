//! Account-local product catalog. It never changes Zalo Business entitlements.

use anyhow::{ensure, Result};
use rusqlite::{params, Connection};

const LIST_LIMIT: i64 = 200;

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Product {
    pub id: String,
    pub sku: String,
    pub name: String,
    pub description: String,
    pub price_vnd: i64,
    pub variants: String,
    pub stock_note: String,
    pub updated_at: i64,
}

/// Version 3 extends the existing account database without changing chat history.
pub(crate) fn init_schema(conn: &Connection) -> Result<()> {
    let version: i64 = conn.query_row("PRAGMA user_version", [], |row| row.get(0))?;
    ensure!(
        version <= 3,
        "Dữ liệu được tạo bởi phiên bản ứng dụng mới hơn"
    );
    if version == 3 {
        return Ok(());
    }
    ensure!(version == 2, "Cần nâng cấp dữ liệu cá nhân trước danh mục");
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
        "INSERT INTO products(id,sku,name,description,price_vnd,variants,stock_note,updated_at)
         VALUES(?1,?2,?3,?4,?5,?6,?7,?8)
         ON CONFLICT(id) DO UPDATE SET sku=excluded.sku,name=excluded.name,
         description=excluded.description,price_vnd=excluded.price_vnd,
         variants=excluded.variants,stock_note=excluded.stock_note,updated_at=excluded.updated_at",
        params![
            product.id,
            product.sku,
            product.name,
            product.description,
            product.price_vnd,
            product.variants,
            product.stock_note,
            product.updated_at
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
        "SELECT id,sku,name,description,price_vnd,variants,stock_note,updated_at
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
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(products)
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
            3
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
            3
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
        conn.execute_batch("PRAGMA user_version=4;").unwrap();
        drop(conn);
        assert!(crate::store::MessageStore::open(path.to_str().unwrap()).is_err());
    }
}

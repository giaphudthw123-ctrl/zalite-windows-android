//! User-owned GIFs imported from local files. No external account access.

use anyhow::{bail, Context, Result};
use image::ImageFormat;
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::{Path, PathBuf};

pub const MAX_GIF_BYTES: u64 = ctdozlite_core::MAX_GIF_BYTES;
pub const MAX_GALLERY_BYTES: u64 = 256 * 1024 * 1024;
pub const MAX_GALLERY_ITEMS: usize = 100;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PersonalGif {
    pub id: String,
    pub name: String,
    pub path: PathBuf,
    pub bytes: u64,
    pub width: u32,
    pub height: u32,
}

fn gallery_dir(root: &Path) -> PathBuf {
    root.join("personal_gifs")
}

fn valid_id(id: &str) -> bool {
    id.len() == 64 && id.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn gif_dimensions(header: &[u8]) -> Result<(u32, u32)> {
    ctdozlite_core::gif_dimensions(header)
        .map(|size| (size.width, size.height))
        .context("Tệp GIF không hợp lệ hoặc vượt giới hạn kích thước")
}

fn clean_name(path: &Path) -> String {
    path.file_name()
        .unwrap_or_default()
        .to_string_lossy()
        .chars()
        .filter(|c| !c.is_control())
        .take(80)
        .collect()
}

pub fn list(root: &Path) -> Result<Vec<PersonalGif>> {
    let dir = gallery_dir(root);
    if !dir.exists() {
        return Ok(Vec::new());
    }
    let mut items = Vec::new();
    for entry in std::fs::read_dir(&dir)? {
        let entry = entry?;
        let path = entry.path();
        let Some(name) = path.file_name().and_then(|n| n.to_str()) else {
            continue;
        };
        let Some(id) = name.strip_suffix(".gif") else {
            continue;
        };
        if !valid_id(id) {
            continue;
        }
        let metadata = path.symlink_metadata()?;
        if !metadata.is_file() || metadata.len() > MAX_GIF_BYTES {
            continue;
        }
        let mut header = [0u8; 10];
        if std::fs::File::open(&path)?.read_exact(&mut header).is_err() {
            continue;
        }
        let Ok((width, height)) = gif_dimensions(&header) else {
            continue;
        };
        let label = std::fs::read(dir.join(format!("{id}.json")))
            .ok()
            .filter(|bytes| bytes.len() <= 512)
            .and_then(|bytes| serde_json::from_slice::<String>(&bytes).ok())
            .unwrap_or_else(|| format!("GIF {}", &id[..8]));
        items.push(PersonalGif {
            id: id.to_string(),
            name: label,
            path,
            bytes: metadata.len(),
            width,
            height,
        });
    }
    items.sort_by(|a, b| a.name.cmp(&b.name));
    items.truncate(MAX_GALLERY_ITEMS);
    Ok(items)
}

pub fn import(root: &Path, source: &Path) -> Result<PersonalGif> {
    let meta = source
        .symlink_metadata()
        .context("Không đọc được tệp GIF")?;
    if !meta.is_file() || meta.len() == 0 || meta.len() > MAX_GIF_BYTES {
        bail!("GIF phải là tệp thường và không vượt quá 8 MB");
    }
    let mut bytes = Vec::with_capacity(meta.len() as usize);
    std::fs::File::open(source)?
        .take(MAX_GIF_BYTES + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() as u64 > MAX_GIF_BYTES {
        bail!("GIF vượt quá 8 MB");
    }
    let (width, height) = gif_dimensions(&bytes)?;
    image::load_from_memory_with_format(&bytes, ImageFormat::Gif)
        .context("Không giải mã được GIF")?;

    let id = format!("{:x}", Sha256::digest(&bytes));
    let dir = gallery_dir(root);
    crate::data_dir::ensure_private_dir(&dir)?;
    let path = dir.join(format!("{id}.gif"));
    let name = clean_name(source);
    match path.symlink_metadata() {
        Ok(existing) => {
            if !existing.is_file() || std::fs::read(&path)? != bytes {
                bail!("GIF đã lưu bị thay đổi hoặc không phải tệp thường");
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            let existing = list(root)?;
            let total = existing.iter().map(|entry| entry.bytes).sum::<u64>();
            if existing.len() >= MAX_GALLERY_ITEMS
                || bytes.len() as u64 > MAX_GALLERY_BYTES.saturating_sub(total)
            {
                bail!("Thư viện GIF đã đạt giới hạn dung lượng hoặc số tệp");
            }
            crate::data_dir::write_private_atomic(&path, &bytes)?;
            let label = serde_json::to_vec(&name)?;
            if let Err(error) =
                crate::data_dir::write_private_atomic(&dir.join(format!("{id}.json")), &label)
            {
                let _ = std::fs::remove_file(&path);
                return Err(error);
            }
        }
        Err(error) => return Err(error.into()),
    }
    Ok(PersonalGif {
        id,
        name,
        path,
        bytes: bytes.len() as u64,
        width,
        height,
    })
}

pub fn remove(root: &Path, id: &str) -> Result<()> {
    if !valid_id(id) {
        bail!("Mã GIF không hợp lệ");
    }
    let dir = gallery_dir(root);
    let path = dir.join(format!("{id}.gif"));
    if !path.symlink_metadata()?.is_file() {
        bail!("GIF không phải tệp thường");
    }
    std::fs::remove_file(path)?;
    let _ = std::fs::remove_file(dir.join(format!("{id}.json")));
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn import_lists_and_removes_local_gif() {
        let nonce = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let root =
            std::env::temp_dir().join(format!("personal-gif-test-{}-{nonce}", std::process::id()));
        std::fs::create_dir_all(&root).unwrap();
        let source = root.join("saved-from-messenger.gif");
        let pixel = image::RgbaImage::from_pixel(2, 2, image::Rgba([255, 0, 0, 255]));
        let mut encoder =
            image::codecs::gif::GifEncoder::new(std::fs::File::create(&source).unwrap());
        encoder.encode_frame(image::Frame::new(pixel)).unwrap();
        drop(encoder);
        let item = import(&root, &source).unwrap();
        assert_eq!(item.name, "saved-from-messenger.gif");
        assert_eq!(list(&root).unwrap().len(), 1);
        assert_eq!(import(&root, &source).unwrap().id, item.id);
        assert!(remove(&root, "../escape").is_err());
        remove(&root, &item.id).unwrap();
        assert!(list(&root).unwrap().is_empty());
        std::fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_non_gif_data() {
        assert!(gif_dimensions(b"not a GIF").is_err());
        let mut header = b"GIF89a".to_vec();
        header.extend_from_slice(&u16::MAX.to_le_bytes());
        header.extend_from_slice(&u16::MAX.to_le_bytes());
        assert!(gif_dimensions(&header).is_err());
    }
}

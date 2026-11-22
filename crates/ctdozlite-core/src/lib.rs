//! Pure local rules shared by desktop and mobile clients.
//! No UI, account, network, or platform storage dependency belongs here.

pub const MAX_GIF_BYTES: u64 = 8 * 1024 * 1024;
pub const MAX_GIF_PIXELS: u64 = 4_000_000;
pub const MAX_QUICK_TEMPLATES: usize = 100;
pub const MAX_TEMPLATE_CHARS: usize = 120;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct GifDimensions {
    pub width: u32,
    pub height: u32,
}

/// Validate the fixed GIF header before attempting a full decoder pass.
pub fn gif_dimensions(header: &[u8]) -> Option<GifDimensions> {
    if header.len() < 10 || !matches!(&header[..6], b"GIF87a" | b"GIF89a") {
        return None;
    }
    let width = u16::from_le_bytes([header[6], header[7]]) as u32;
    let height = u16::from_le_bytes([header[8], header[9]]) as u32;
    if width == 0 || height == 0 || u64::from(width) * u64::from(height) > MAX_GIF_PIXELS {
        return None;
    }
    Some(GifDimensions { width, height })
}

/// Keep message templates editable and safe to display in either UI.
pub fn normalize_template(input: &str) -> Option<String> {
    let text = input.trim();
    if text.is_empty() || text.chars().count() > MAX_TEMPLATE_CHARS {
        return None;
    }
    if text
        .chars()
        .any(|c| c.is_control() && c != '\n' && c != '\t')
    {
        return None;
    }
    Some(text.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checks_gif_header_and_pixel_budget() {
        assert_eq!(
            gif_dimensions(b"GIF89a\x02\x00\x03\x00"),
            Some(GifDimensions {
                width: 2,
                height: 3
            })
        );
        assert_eq!(
            gif_dimensions(b"GIF87a\x02\x00\x03\x00"),
            Some(GifDimensions {
                width: 2,
                height: 3
            })
        );
        assert_eq!(gif_dimensions(b"GIF89a\xff\xff\xff\xff"), None);
        assert_eq!(gif_dimensions(b"not gif!!!"), None);
    }

    #[test]
    fn template_normalizes_and_rejects_invalid_input() {
        assert_eq!(normalize_template("  Xin chào  "), Some("Xin chào".into()));
        assert_eq!(normalize_template("  "), None);
        assert_eq!(normalize_template("bad\0text"), None);
        assert_eq!(
            normalize_template(&"a".repeat(MAX_TEMPLATE_CHARS + 1)),
            None
        );
    }

    #[test]
    fn variables_expand_once_and_preserve_unknown_unicode() {
        assert_eq!(
            expand_local_variables(
                "Chào {ten} {gio} {ngay} {khac}",
                "An {gio}",
                "08:30",
                "22/11/2026"
            ),
            "Chào An {gio} 08:30 22/11/2026 {khac}"
        );
        assert_eq!(
            expand_local_variables("{ten}{ten}ế{{gio}", "", "09:00", ""),
            "ế{09:00"
        );
    }
}

/// Expand supported placeholders once; replacement values are never interpreted again.
pub fn expand_local_variables(content: &str, recipient: &str, time: &str, date: &str) -> String {
    let mut result = String::with_capacity(content.len() + recipient.len());
    let mut rest = content;
    while !rest.is_empty() {
        let replacement = if rest.starts_with("{ten}") {
            Some(("{ten}", recipient))
        } else if rest.starts_with("{gio}") {
            Some(("{gio}", time))
        } else if rest.starts_with("{ngay}") {
            Some(("{ngay}", date))
        } else {
            None
        };
        if let Some((token, value)) = replacement {
            result.push_str(value);
            rest = &rest[token.len()..];
        } else if let Some(ch) = rest.chars().next() {
            result.push(ch);
            rest = &rest[ch.len_utf8()..];
        }
    }
    result
}

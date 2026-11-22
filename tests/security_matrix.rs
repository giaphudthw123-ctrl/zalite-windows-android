//! Deterministic adversarial matrices: 1,024 distinct input scenarios, not 1,024 test functions.
use zalo_rust::{business_catalog as catalog, session, store::MessageStore};

#[test]
fn credential_destination_matrix_rejects_256_untrusted_urls() {
    let domains = ["zalo.me", "zaloapp.com", "zadn.vn", "zdn.vn"];
    let mut cases = 0;
    for domain in domains {
        for index in 0..16 {
            let urls = [
                format!("https://{domain}.attacker-{index}.invalid/chat"),
                format!("https://{domain}@attacker-{index}.invalid/chat"),
                format!("http://node-{index}.{domain}/chat"),
                format!("https://node-{index}.{domain}:8443/chat"),
            ];
            for url in urls {
                assert!(
                    !session::is_trusted_zalo_url(&url),
                    "credential escape: {url}"
                );
                cases += 1;
            }
        }
    }
    assert_eq!(cases, 256);
    println!("credential destinations: {cases} scenarios passed");
}

#[test]
fn managed_image_matrix_rejects_256_path_escapes() {
    let digest = "a".repeat(64);
    let mut cases = 0;
    for index in 0..32 {
        let paths = [
            format!("../account-{index}/{digest}.png"),
            format!("product-images/../{digest}-{index}.png"),
            format!("product-images/account-{index}/../../{digest}.png"),
            format!("product-images/account-{index}/{digest}.exe"),
            format!("product-images/account-{index}/{digest}.png:payload"),
            format!("product-images/account-{index}/{digest}.png/extra"),
            format!("other-{index}/{digest}.png"),
            format!("product-images/account-{index}/short.png"),
        ];
        for path in paths {
            assert!(
                catalog::managed_image_path(std::path::Path::new("."), &path).is_err(),
                "path escape: {path}"
            );
            cases += 1;
        }
    }
    assert_eq!(cases, 256);
    println!("managed image paths: {cases} scenarios passed");
}

#[test]
fn catalog_control_matrix_checks_320_field_inputs_without_partial_writes() {
    let store = MessageStore::open(":memory:").unwrap();
    let controls = (0..32).chain(127..159);
    let mut cases = 0;
    for codepoint in controls {
        for field in 0..5 {
            let mut product = catalog::Product {
                name: "Fixture".into(),
                ..Default::default()
            };
            let value = format!("prefix{}suffix", char::from_u32(codepoint).unwrap());
            match field {
                0 => product.sku = value,
                1 => product.name = value,
                2 => product.description = value,
                3 => product.variants = value,
                _ => product.stock_note = value,
            }
            let before: i64 = store
                .conn
                .query_row("SELECT COUNT(*) FROM products", [], |row| row.get(0))
                .unwrap();
            let result = catalog::save(&store.conn, product, 1);
            let allowed = field == 2 && codepoint == 10;
            assert_eq!(result.is_ok(), allowed, "field={field}, U+{codepoint:04X}");
            let after: i64 = store
                .conn
                .query_row("SELECT COUNT(*) FROM products", [], |row| row.get(0))
                .unwrap();
            assert_eq!(
                after,
                before + i64::from(allowed),
                "partial write field={field}, U+{codepoint:04X}"
            );
            cases += 1;
        }
    }
    assert_eq!(cases, 320);
    println!("catalog control characters: {cases} scenarios passed");
}

#[test]
fn unicode_field_matrix_checks_192_boundaries_and_persistence() {
    let mut cases = 0;
    for (field, limit) in [80usize, 160, 500, 1000].into_iter().enumerate() {
        for offset in -23isize..=24 {
            let store = MessageStore::open(":memory:").unwrap();
            let length = (limit as isize + offset) as usize;
            let value = "ế".repeat(length);
            let mut product = catalog::Product {
                name: "Fixture".into(),
                ..Default::default()
            };
            match field {
                0 => product.sku = value.clone(),
                1 => product.name = value.clone(),
                2 => product.variants = value.clone(),
                _ => product.stock_note = value.clone(),
            }
            let result = catalog::save(&store.conn, product, 1);
            assert_eq!(
                result.is_ok(),
                length <= limit,
                "field={field}, length={length}"
            );
            let rows = catalog::list(&store.conn, "").unwrap();
            if length <= limit {
                assert_eq!(rows.len(), 1);
                let actual = match field {
                    0 => &rows[0].sku,
                    1 => &rows[0].name,
                    2 => &rows[0].variants,
                    _ => &rows[0].stock_note,
                };
                assert_eq!(
                    actual, &value,
                    "Unicode roundtrip field={field}, length={length}"
                );
            } else {
                assert!(rows.is_empty(), "invalid input persisted");
            }
            cases += 1;
        }
    }
    assert_eq!(cases, 192);
    println!("Unicode boundaries and persistence: {cases} scenarios passed");
}

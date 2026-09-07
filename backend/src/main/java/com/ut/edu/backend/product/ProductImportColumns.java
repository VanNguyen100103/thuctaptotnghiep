package com.ut.edu.backend.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which spreadsheet column holds which product field, resolved from the
 * sheet's own header row instead of assumed by position.
 *
 * ProductImportService used to read fixed indices 0..14 matching its own
 * generateTemplate() layout. That only ever worked for files produced by
 * this app: a real KiotViet "DanhSachSanPham" export carries ~26 columns in
 * a different order (Giá vốn at K, Tồn kho at L, and the image column
 * "Hình ảnh (url1,url2...)" at Y), so importing one under the fixed layout
 * silently wrote the wrong cell into every field.
 *
 * {@link #fromHeaderRow} matches header text by name (accent-sensitive but
 * case-, whitespace- and parenthetical-insensitive - "Nhóm hàng(3 Cấp)",
 * "Nhóm hàng (3 cấp)" and "Nhóm hàng" all resolve to the same field), and
 * falls back to {@link #fixedLayout()} when the row doesn't name at least
 * the three required fields - so a sheet with no usable header (or one
 * hand-built from the old template without header text) imports exactly as
 * it did before.
 *
 * Columns a KiotViet export carries but this app has no home for (Thuộc
 * tính, Mã HH Liên quan, Trọng lượng, the per-branch "Kho: ..." stock
 * columns, VAT) are simply left unmapped.
 */
record ProductImportColumns(
        int productType, int categoryPath, int sku, int barcode, int name, int brand,
        int price, int costPrice, int stock, int minStock, int maxStock, int unit,
        int baseUnitSku, int description, int imageUrls, boolean fromHeaderRow) {

    /** Index for a field the sheet doesn't carry; cellStr/cellDecimal read it as blank. */
    static final int ABSENT = -1;

    /**
     * Header aliases per field, in the normalized form {@link #normalize}
     * produces. Every alias here is a full normalized header, matched
     * exactly - a prefix match would confuse "Mã hàng" with a KiotViet
     * export's "Mã HH Liên quan".
     *
     * The rank breaks ties when one sheet carries several headers for the
     * same field, and it is not about position: a real KiotViet export
     * prices each row THREE times - "Giá bán trước thuế" (G), "Giá bán sau
     * thuế" (I) and, in the app's own template, plain "Giá bán". Taking
     * whichever came first would have made this app sell at the pre-VAT
     * price, so the after-tax column - the amount the shop actually charges
     * - outranks it despite sitting further right.
     */
    private record Alias(String field, int rank) {
    }

    private static final Map<String, Alias> ALIASES = new HashMap<>();

    private static void alias(String field, int rank, String... headers) {
        for (String header : headers) {
            ALIASES.put(header, new Alias(field, rank));
        }
    }

    static {
        alias("productType", 0, "loại hàng");
        alias("categoryPath", 0, "nhóm hàng", "nhóm hàng hóa");
        alias("sku", 0, "mã hàng", "mã hàng hóa");
        alias("barcode", 0, "mã vạch");
        alias("name", 0, "tên hàng", "tên hàng hóa");
        alias("brand", 0, "thương hiệu");
        alias("price", 0, "giá bán");
        alias("price", 1, "giá bán sau thuế");
        alias("price", 2, "giá bán trước thuế");
        alias("costPrice", 0, "giá vốn");
        alias("stock", 0, "tồn kho");
        alias("minStock", 0, "tồn nhỏ nhất", "định mức tồn ít nhất");
        alias("maxStock", 0, "tồn lớn nhất", "định mức tồn nhiều nhất");
        alias("unit", 0, "đvt", "đơn vị tính");
        alias("baseUnitSku", 0, "mã đvt cơ bản");
        alias("description", 0, "mô tả", "mô tả chi tiết");
        alias("imageUrls", 0, "hình ảnh", "ảnh", "link ảnh");
    }

    /** The layout generateTemplate() writes - and what an unrecognizable header row falls back to. */
    static ProductImportColumns fixedLayout() {
        return new ProductImportColumns(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, false);
    }

    /**
     * Maps the given header row by name, or returns {@link #fixedLayout()}
     * when it doesn't name Mã hàng, Tên hàng and Giá bán - the three fields
     * a row is rejected without, so a header that misses any of them can't
     * be trusted to describe the rest either.
     *
     * On duplicate headers the leftmost wins, matching how a person reading
     * the sheet would resolve it.
     */
    static ProductImportColumns fromHeaderRow(String[] headerCells) {
        Map<String, Integer> found = new HashMap<>();
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < headerCells.length; i++) {
            Alias alias = ALIASES.get(normalize(headerCells[i]));
            if (alias == null) {
                continue;
            }
            Integer bestRank = ranks.get(alias.field());
            // Strictly-better only, so equally ranked duplicates keep the
            // leftmost - how a person reading the sheet would resolve it.
            if (bestRank == null || alias.rank() < bestRank) {
                ranks.put(alias.field(), alias.rank());
                found.put(alias.field(), i);
            }
        }
        if (!found.containsKey("sku") || !found.containsKey("name") || !found.containsKey("price")) {
            return fixedLayout();
        }
        return new ProductImportColumns(
                found.getOrDefault("productType", ABSENT),
                found.getOrDefault("categoryPath", ABSENT),
                found.get("sku"),
                found.getOrDefault("barcode", ABSENT),
                found.get("name"),
                found.getOrDefault("brand", ABSENT),
                found.get("price"),
                found.getOrDefault("costPrice", ABSENT),
                found.getOrDefault("stock", ABSENT),
                found.getOrDefault("minStock", ABSENT),
                found.getOrDefault("maxStock", ABSENT),
                found.getOrDefault("unit", ABSENT),
                found.getOrDefault("baseUnitSku", ABSENT),
                found.getOrDefault("description", ABSENT),
                found.getOrDefault("imageUrls", ABSENT),
                true);
    }

    /**
     * Lowercases, collapses whitespace (real exports contain non-breaking
     * spaces), drops a trailing "*" required-marker, and strips a trailing
     * parenthetical so "Hình ảnh (url1,url2...)" and "Nhóm hàng(3 Cấp)"
     * match on their leading words. Accents are kept: "Mã vạch" and "Ma
     * vach" are different headers, and no real export writes the latter.
     */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        // U+00A0 (non-breaking space) is not matched by \s but does appear in real exports.
        String s = raw.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
        int paren = s.indexOf('(');
        if (paren > 0) {
            s = s.substring(0, paren);
        }
        while (s.endsWith("*")) {
            s = s.substring(0, s.length() - 1);
        }
        return collapseSpaces(s);
    }

    /** " Mã   hàng " -> "mã hàng", without a regex (the source-level escaping of one is easy to get subtly wrong). */
    private static String collapseSpaces(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean pendingSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = !out.isEmpty();
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Human-readable summary for the import log, so a mis-mapped file is diagnosable from Render's logs alone. */
    String describe() {
        if (!fromHeaderRow) {
            return "layout cố định (không nhận diện được dòng tiêu đề)";
        }
        List<String> missing = new ArrayList<>();
        if (categoryPath == ABSENT) missing.add("Nhóm hàng");
        if (costPrice == ABSENT) missing.add("Giá vốn");
        if (stock == ABSENT) missing.add("Tồn kho");
        if (imageUrls == ABSENT) missing.add("Hình ảnh");
        return "map theo tiêu đề" + (missing.isEmpty() ? "" : ", thiếu cột: " + String.join(", ", missing));
    }
}

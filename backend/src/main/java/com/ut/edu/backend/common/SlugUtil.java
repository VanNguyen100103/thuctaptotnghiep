package com.ut.edu.backend.common;

import java.text.Normalizer;

/**
 * Server-side slug generation - needed only for
 * AdminProductController#createProductVariants, which derives one slug per
 * generated row itself rather than trusting a client-supplied slug (there's
 * no per-row slug field in that request). Mirrors
 * frontend/src/app/features/dashboard/slugify.ts's logic exactly.
 */
public class SlugUtil {

    private SlugUtil() {
    }

    /** "Áo Thun Basic" -> "ao-thun-basic". Diacritic-stripping via NFD normalization. */
    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[đĐ]", "d")
                .toLowerCase()
                .trim();
        return normalized
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}

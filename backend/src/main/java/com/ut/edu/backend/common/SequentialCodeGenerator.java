package com.ut.edu.backend.common;

/**
 * Shared "NCC000001" / "PN000001" style code generation - a fixed prefix plus
 * a 6-digit, zero-padded, 1-based sequence number. Used by Supplier and
 * PurchaseOrder, both of which retry with the next number on a unique
 * constraint collision (see PurchaseOrderService/SupplierController) rather
 * than relying on a DB sequence, since the number must be scoped per store,
 * not global.
 */
public final class SequentialCodeGenerator {

    private SequentialCodeGenerator() {
    }

    public static String generate(String prefix, long countSoFar) {
        return prefix + String.format("%06d", countSoFar + 1);
    }
}

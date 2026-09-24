package com.ut.edu.backend.automation;

/**
 * The event keys this app promises to n8n.
 *
 * They are part of a contract with workflows living outside this repository,
 * so they are constants rather than an enum's {@code name()}: renaming a Java
 * symbol must not be able to silently break a shop's automation, and a key
 * deliberately reads as the dotted string the workflow's Switch node matches on.
 */
public final class AutomationEvents {

    /** A storefront checkout wrote an order (payment not yet taken - see OrderController). */
    public static final String ORDER_CREATED = "order.created";

    /** The register took payment and the invoice is closed (SaleService#checkout). */
    public static final String SALE_COMPLETED = "sale.completed";

    /**
     * A product just fell to or below its "Định mức tồn ít nhất"
     * (Product#minStockThreshold). Raised on the crossing only, not on every
     * later sale of an already-low product - otherwise a popular item that
     * nobody restocks would send an alert per unit sold.
     */
    public static final String INVENTORY_LOW_STOCK = "inventory.low_stock";

    private AutomationEvents() {
    }
}

package com.ut.edu.backend.salereturn;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One line the customer is handing back. Carries no price: what a return is
 * worth is what the invoice line charged, and letting the client send that
 * would let it refund more than was ever paid. See SaleReturnService#create.
 */
public record SaleReturnItemRequest(
        @NotNull(message = "saleItemId is required") Long saleItemId,
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1") Integer quantity) {
}

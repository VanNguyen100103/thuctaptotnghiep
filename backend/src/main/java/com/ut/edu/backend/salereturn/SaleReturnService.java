package com.ut.edu.backend.salereturn;

import com.ut.edu.backend.common.SequentialCodeGenerator;
import com.ut.edu.backend.product.Product;
import com.ut.edu.backend.product.ProductRepository;
import com.ut.edu.backend.sale.Customer;
import com.ut.edu.backend.sale.CustomerRepository;
import com.ut.edu.backend.sale.Sale;
import com.ut.edu.backend.sale.SaleItem;
import com.ut.edu.backend.sale.SaleRepository;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Trả hàng" business logic - the counter taking goods back off an invoice.
 *
 * Mirrors SaleService rather than PurchaseReturnService: one call writes the
 * document, puts the goods back on the shelf, settles the loyalty points and
 * names the refund, because a return at the register is finished the moment
 * the customer walks away. There is no draft to save and no second
 * "Hoàn thành" click.
 *
 * The money is the part worth reading twice. A refund is not the sum of the
 * returned lines: an invoice that got a discount, spent a coupon or redeemed
 * points collected less than its lines add up to, so the returned goods take
 * their share of all three off the refund - and the redeemed points go back
 * to the customer instead, since they bought goods that are no longer theirs.
 * Handing back the raw line totals would refund money the till never took.
 *
 * Deliberately left alone:
 * <ul>
 *   <li>{@code Product#soldCount} - it counts units that went over the
 *       counter, which they did. It drives the storefront popularity badge
 *       ("đã bán"), not any stock figure, and a return is not evidence the
 *       sale never happened.</li>
 *   <li>The coupon usedCount - the code was used; a return does not hand back
 *       a use of it.</li>
 *   <li>{@code Sale#shippingFee} and {@code Sale#otherCollectionAmount} - a
 *       delivery that was made and a surcharge that was charged are services
 *       already rendered, so returning goods does not refund either.</li>
 *   <li>Taxable revenue (TaxRevenueService) - a refund against a quarter
 *       already declared is a tờ khai bổ sung, not a silent edit of a filed
 *       number. See that class for the same rule stated from the other side.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaleReturnService {

    private static final String CODE_PREFIX = "TH";
    private static final int MAX_CODE_RETRIES = 5;

    /** VND columns are numeric(_, 2) throughout, so every prorated share lands on the same scale. */
    private static final int MONEY_SCALE = 2;

    private final SaleReturnRepository saleReturnRepository;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final TenantGuard tenantGuard;

    /**
     * The invoice a return is about to be written against, with every line
     * remaining returnable quantity - what the form opens on, and what
     * {@link #create} re-checks before it writes anything.
     */
    @Transactional(readOnly = true)
    public ReturnableSaleResponse findReturnable(Long saleId) {
        Sale sale = requireStoreSale(saleId);
        return ReturnableSaleResponse.from(sale, returnedQuantities(saleId),
                saleReturnRepository.sumPointsRestoredBySale(saleId),
                saleReturnRepository.sumPointsRevertedBySale(saleId));
    }

    @Transactional
    public SaleReturn create(Long storeId, User cashier, CreateSaleReturnRequest request) {
        Sale sale = requireStoreSale(request.saleId());

        Map<Long, SaleItem> saleLines = sale.getItems().stream()
                .collect(Collectors.toMap(SaleItem::getId, Function.identity()));
        Map<Long, Integer> alreadyReturned = returnedQuantities(sale.getId());

        SaleReturn saleReturn = SaleReturn.builder()
                .store(tenantGuard.currentStoreRef())
                .sale(sale)
                // Snapshotted off the invoice: a return goes to whoever bought,
                // and the register does not get to name someone else.
                .customer(sale.getCustomer())
                .refundMethod(request.refundMethod())
                .returnFee(nz(request.returnFee()))
                .note(request.note())
                .createdBy(cashier)
                .build();

        BigDecimal totalGoodsValue = BigDecimal.ZERO;
        Set<Long> seenLines = new HashSet<>();
        for (SaleReturnItemRequest itemReq : request.items()) {
            if (!seenLines.add(itemReq.saleItemId())) {
                throw new IllegalArgumentException("Mỗi dòng hàng chỉ được khai báo một lần trên phiếu trả");
            }
            SaleItem soldLine = saleLines.get(itemReq.saleItemId());
            if (soldLine == null) {
                throw new IllegalArgumentException("Hóa đơn " + sale.getCode() + " không có dòng hàng này");
            }
            int remaining = soldLine.getQuantity() - alreadyReturned.getOrDefault(soldLine.getId(), 0);
            if (itemReq.quantity() > remaining) {
                throw new IllegalArgumentException("Hàng hóa \"" + soldLine.getProductName()
                        + "\" chỉ còn " + remaining + " có thể trả trên hóa đơn này");
            }

            // Prorated by units, so returning 1 of 3 discounted units gives
            // back a third of that line discount, not all of it and not none.
            BigDecimal lineDiscountShare = share(soldLine.getDiscountAmount(), itemReq.quantity(), soldLine.getQuantity());
            BigDecimal lineTotal = soldLine.getUnitPrice()
                    .multiply(BigDecimal.valueOf(itemReq.quantity()))
                    .subtract(lineDiscountShare);

            saleReturn.addItem(SaleReturnItem.builder()
                    .saleItem(soldLine)
                    .product(soldLine.getProduct())
                    .productName(soldLine.getProductName())
                    .productSku(soldLine.getProductSku())
                    .quantity(itemReq.quantity())
                    .unitPrice(soldLine.getUnitPrice())
                    .discountAmount(lineDiscountShare)
                    .lineTotal(lineTotal)
                    .build());
            totalGoodsValue = totalGoodsValue.add(lineTotal);
        }
        saleReturn.setTotalGoodsValue(totalGoodsValue);

        // Everything spread across the whole invoice - its discount, its
        // coupon, its points - is split on one fraction: how much of the
        // invoice goods is coming back. That fraction is never materialised as
        // a decimal, only ever applied as totalGoodsValue / subtotal in one
        // division, or a third of an invoice would round short.
        BigDecimal invoiceDiscounts = sale.getDiscountAmount()
                .add(sale.getCouponDiscountAmount())
                .add(sale.getPointsRedeemedAmount());
        // Capped at the goods themselves: rounding on a many-line invoice must
        // never be able to push the refund below zero.
        BigDecimal discountShare =
                returnedShare(invoiceDiscounts, totalGoodsValue, sale.getSubtotal()).min(totalGoodsValue);
        saleReturn.setDiscountAmount(discountShare);

        BigDecimal refundableGoods = totalGoodsValue.subtract(discountShare);
        if (saleReturn.getReturnFee().compareTo(refundableGoods) > 0) {
            throw new IllegalArgumentException("Phí trả hàng không được lớn hơn tiền hàng trả lại ("
                    + refundableGoods.toPlainString() + ")");
        }
        saleReturn.setRefundAmount(refundableGoods.subtract(saleReturn.getReturnFee()));

        applyLoyaltyPoints(saleReturn, sale, totalGoodsValue);
        restockReturnedGoods(saleReturn);

        // Retry on the rare race where two requests generate the same
        // next-in-sequence code concurrently (same pattern as SaleService).
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
            saleReturn.setCode(SequentialCodeGenerator.generate(
                    CODE_PREFIX, saleReturnRepository.countByStoreId(storeId) + attempt));
            try {
                SaleReturn saved = saleReturnRepository.save(saleReturn);
                log.info("Sale return {} created against invoice {}: {} line(s), refund {} by {}",
                        saved.getCode(), sale.getCode(), saved.getItems().size(),
                        saved.getRefundAmount(), saved.getRefundMethod());
                return saved;
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    /**
     * Points move both ways on a return. Whatever the customer spent on the
     * goods coming back is theirs again; whatever those goods earned them is
     * taken off. Both are prorated, and both are capped by what earlier
     * returns of the same invoice already moved, so repeated partial returns
     * can never add up past the two numbers on the invoice itself.
     */
    private void applyLoyaltyPoints(SaleReturn saleReturn, Sale sale, BigDecimal totalGoodsValue) {
        int restored = cappedShare(sale.getPointsRedeemed(), totalGoodsValue, sale.getSubtotal(),
                saleReturnRepository.sumPointsRestoredBySale(sale.getId()));
        int reverted = cappedShare(sale.getPointsEarned(), totalGoodsValue, sale.getSubtotal(),
                saleReturnRepository.sumPointsRevertedBySale(sale.getId()));
        saleReturn.setPointsRestored(restored);
        saleReturn.setPointsReverted(reverted);

        Customer customer = sale.getCustomer();
        if (customer == null || (restored == 0 && reverted == 0)) {
            return;
        }
        // Floored at zero: a customer who has already spent the points earned
        // here is not chased into a negative balance - the shop has the goods
        // back, which is the part that matters.
        customer.setLoyaltyPoints(Math.max(0, customer.getLoyaltyPoints() + restored - reverted));
        customerRepository.save(customer);
    }

    /**
     * Goods come back onto the shelf under the same pessimistic lock the sale
     * that sold them used, so a return and a sale of one product cannot lose
     * an update between them.
     *
     * A line whose product has since been deleted is refunded but not
     * restocked - the opposite of PurchaseReturnService, which blocks. There,
     * skipping a line would hand goods to the supplier and take nothing off
     * the shelf; here, refusing would leave a customer holding goods and no
     * money because the shop tidied its catalog.
     */
    private void restockReturnedGoods(SaleReturn saleReturn) {
        for (SaleReturnItem item : saleReturn.getItems()) {
            if (item.getProduct() == null) {
                log.warn("Sale return line \"{}\" has no product left in the catalog - refunded without restocking",
                        item.getProductName());
                continue;
            }
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + item.getProduct().getId()));
            product.incrementStock(item.getQuantity());
            productRepository.save(product);
        }
    }

    private Sale requireStoreSale(Long saleId) {
        return saleRepository.findById(saleId)
                .filter(sale -> tenantGuard.isCurrentStore(sale.getStore()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hóa đơn: " + saleId));
    }

    /** How many units of each line of this invoice earlier returns already took. */
    private Map<Long, Integer> returnedQuantities(Long saleId) {
        Map<Long, Integer> returned = new HashMap<>();
        for (SaleReturnRepository.ReturnedQuantity row : saleReturnRepository.sumReturnedQuantitiesBySale(saleId)) {
            returned.put(row.getSaleItemId(), row.getQuantity() == null ? 0 : row.getQuantity().intValue());
        }
        return returned;
    }

    /** A money figure share of {@code part} out of {@code whole} units. */
    private static BigDecimal share(BigDecimal amount, int part, int whole) {
        if (amount == null || amount.signum() == 0 || whole <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(part))
                .divide(BigDecimal.valueOf(whole), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** An invoice-wide money figure, scaled down to the goods actually coming back. */
    private static BigDecimal returnedShare(BigDecimal amount, BigDecimal returnedGoods, BigDecimal saleSubtotal) {
        if (amount == null || amount.signum() == 0 || saleSubtotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(returnedGoods).divide(saleSubtotal, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The same share of a point total, rounded DOWN (never invent a point) and
     * capped by what earlier returns of this invoice already used up.
     *
     * Multiplied before it is divided, so returning exactly a third of an
     * invoice gives back exactly a third of its points - going via a decimal
     * ratio first would leave 30 x 0.3333... one point short.
     */
    private static int cappedShare(Integer total, BigDecimal returnedGoods, BigDecimal saleSubtotal, int alreadyUsed) {
        if (total == null || total <= 0 || saleSubtotal.signum() == 0) {
            return 0;
        }
        int wanted = BigDecimal.valueOf(total).multiply(returnedGoods)
                .divide(saleSubtotal, 0, RoundingMode.DOWN)
                .intValue();
        return Math.max(0, Math.min(wanted, total - alreadyUsed));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

package com.ut.edu.backend.tax;

import com.ut.edu.backend.order.OrderRepository;
import com.ut.edu.backend.order.OrderStatus;
import com.ut.edu.backend.order.SalesChannel;
import com.ut.edu.backend.sale.SaleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * "Doanh thu tính thuế" for a declaration period, read off what the shop
 * actually sold.
 *
 * Two sources, because the shop has two tills: the register (Sale) and the
 * online channels (Order). Splitting them here rather than in the controller
 * keeps the one rule that matters in a single place - what counts as revenue
 * and what does not - so the list screen, the detail screen and the snapshot
 * written at filing time can never disagree about a number the tax office
 * will read.
 */
@Service
@RequiredArgsConstructor
public class TaxRevenueService {

    /**
     * The order states that are money the shop has, or is certainly owed by
     * the carrier.
     *
     * Wider than the dashboard's {DELIVERED, PAID} on purpose. A household
     * business declares revenue when the sale is done, and three more states
     * mean exactly that: COMPLETED is the carrier signing off, COD_SETTLEMENT
     * is cash already collected from the customer and sitting at Goship, and
     * PARTIALLY_DELIVERED is goods handed over and paid for. Leaving them out
     * would under-declare - the expensive direction to be wrong in.
     *
     * Everything else is deliberately absent: PENDING/PROCESSING and the
     * in-transit states are not finished sales, and CANCELLED, RETURNED,
     * REFUNDED, LOST and FAILED are sales that came undone. A refund after
     * the quarter was filed is a tờ khai bổ sung, not a silent edit.
     */
    private static final List<OrderStatus> REVENUE_STATUSES = List.of(
            OrderStatus.PAID,
            OrderStatus.DELIVERED,
            OrderStatus.PARTIALLY_DELIVERED,
            OrderStatus.COD_SETTLEMENT,
            OrderStatus.COMPLETED);

    /** Every channel except POS_DELIVERY, whose money is already on the Sale - see OrderRepository#sumStoreRevenueBetween. */
    private static final List<SalesChannel> ONLINE_CHANNELS = Arrays.stream(SalesChannel.values())
            .filter(c -> c != SalesChannel.POS_DELIVERY)
            .toList();

    private final SaleRepository saleRepository;
    private final OrderRepository orderRepository;

    /**
     * What one period contributed, split by till.
     *
     * @param posRevenue      counter sales ("Hóa đơn" - Sale)
     * @param posCount        how many of them
     * @param onlineRevenue   storefront and marketplace orders that completed
     * @param onlineCount     how many of them
     */
    public record RevenueBreakdown(BigDecimal posRevenue, long posCount,
                                   BigDecimal onlineRevenue, long onlineCount) {

        public BigDecimal total() {
            return posRevenue.add(onlineRevenue);
        }

        public long totalCount() {
            return posCount + onlineCount;
        }

        public static RevenueBreakdown empty() {
            return new RevenueBreakdown(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0);
        }
    }

    @Transactional(readOnly = true)
    public RevenueBreakdown between(Long storeId, LocalDateTime from, LocalDateTime to) {
        return new RevenueBreakdown(
                nz(saleRepository.sumRevenueBetween(storeId, from, to)),
                saleRepository.countBetween(storeId, from, to),
                nz(orderRepository.sumStoreRevenueBetween(storeId, REVENUE_STATUSES, ONLINE_CHANNELS, from, to)),
                orderRepository.countStoreRevenueBetween(storeId, REVENUE_STATUSES, ONLINE_CHANNELS, from, to));
    }

    @Transactional(readOnly = true)
    public RevenueBreakdown forPeriod(Long storeId, TaxPeriod period) {
        return between(storeId, period.startAt(), period.endAt());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

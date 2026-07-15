package com.ut.edu.backend.order;

import com.ut.edu.backend.user.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :datetime")
    List<Order> findByStatusAndCreatedAtBefore(@Param("status") OrderStatus status,
                                                @Param("datetime") LocalDateTime datetime);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.user.id = :userId AND o.status = 'DELIVERED'")
    java.math.BigDecimal getTotalSpentByUserId(@Param("userId") Long userId);

    Boolean existsByOrderNumber(String orderNumber);

    // ==================== DASHBOARD OPTIMIZATION QUERIES ====================

    /**
     * Count orders by status
     */
    Long countByStatus(OrderStatus status);

    /**
     * Count orders by multiple statuses
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :statuses")
    Long countByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    /**
     * Calculate total revenue for delivered/paid orders
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.status IN :statuses")
    java.math.BigDecimal sumTotalByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    /**
     * Calculate revenue for specific date range and statuses
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.createdAt >= :startDate AND o.status IN :statuses")
    java.math.BigDecimal sumTotalByCreatedAtAfterAndStatusIn(
            @Param("startDate") LocalDateTime startDate,
            @Param("statuses") List<OrderStatus> statuses);

    /**
     * Get orders for sales statistics (with date range and status)
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<Order> findByCreatedAtAfterAndStatusIn(
            @Param("startDate") LocalDateTime startDate,
            @Param("statuses") List<OrderStatus> statuses);

    /**
     * Get orders for sales statistics with pagination (OPTIMIZED)
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate AND o.status IN :statuses ORDER BY o.createdAt DESC")
    Page<Order> findByCreatedAtAfterAndStatusInPaginated(
            @Param("startDate") LocalDateTime startDate,
            @Param("statuses") List<OrderStatus> statuses,
            Pageable pageable);

    /**
     * Get delivered orders for top products analysis (OPTIMIZED with JOIN FETCH)
     * This prevents N+1 query problem by eagerly loading items and products
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.status = :status " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByStatusOrderByCreatedAtDesc(@Param("status") OrderStatus status);

    /**
     * Get order count and revenue grouped by status
     */
    @Query("SELECT o.status as status, COUNT(o) as count, COALESCE(SUM(o.total), 0) as revenue " +
           "FROM Order o GROUP BY o.status")
    List<Object[]> getOrderStatisticsByStatus();

    /**
     * Get recent orders with limit
     */
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findRecentOrders(Pageable pageable);

    // ==================== AI CLUSTERING QUERIES ====================

    /**
     * Count orders by user for clustering
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user = :user")
    Long countByUser(@Param("user") com.ut.edu.backend.user.User user);

    /**
     * Sum total spent by user for clustering
     * Include PROCESSING, SHIPPED, and DELIVERED orders (exclude PENDING, CANCELLED, PAYMENT_FAILED)
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.user = :user AND o.status IN ('COMPLETED', 'SHIPPED', 'DELIVERED', 'PAID')")
    java.math.BigDecimal sumTotalByUser(@Param("user") com.ut.edu.backend.user.User user);
}

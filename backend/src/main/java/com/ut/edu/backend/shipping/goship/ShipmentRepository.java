package com.ut.edu.backend.shipping.goship;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    /**
     * Webhook lookups run outside the tenant-filtered request context
     * (Goship calls in with no store JWT), so these are plain unfiltered
     * finds. Two of them because a webhook can identify a shipment either
     * way, and the booking being asynchronous means the Goship id may not
     * have reached us when the first one arrives.
     */
    Optional<Shipment> findByGoshipId(String goshipId);

    Optional<Shipment> findByOrderRef(String orderRef);

    /**
     * Parcels the sweep should still be asking Goship about: not yet at a
     * status that ends the order, and young enough to plausibly still move.
     *
     * Unfiltered by tenant on purpose - the sweep runs outside any request, so
     * there is no current store, and it covers every shop's parcels. Oldest
     * first so a backlog drains in order rather than starving the front of it.
     */
    @Query("SELECT s FROM Shipment s WHERE s.createdAt >= :since "
            + "AND (s.statusCode IS NULL OR s.statusCode NOT IN :finalCodes) "
            + "ORDER BY s.createdAt ASC")
    List<Shipment> findInFlight(@Param("finalCodes") List<Integer> finalCodes,
                                @Param("since") LocalDateTime since,
                                Pageable pageable);
}

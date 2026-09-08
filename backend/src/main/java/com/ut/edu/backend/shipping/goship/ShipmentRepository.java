package com.ut.edu.backend.shipping.goship;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
}

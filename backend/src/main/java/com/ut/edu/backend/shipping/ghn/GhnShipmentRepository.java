package com.ut.edu.backend.shipping.ghn;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GhnShipmentRepository extends JpaRepository<GhnShipment, Long>, JpaSpecificationExecutor<GhnShipment> {

    /** Webhook lookups run outside the tenant-filtered request context (GHN calls in with no store JWT), so this is a plain unfiltered find by GHN's own tracking code. */
    Optional<GhnShipment> findByGhnOrderCode(String ghnOrderCode);
}

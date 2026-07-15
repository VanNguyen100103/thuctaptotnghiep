package com.ut.edu.backend.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findBySlug(String slug);

    Optional<Store> findBySlugAndStatusNot(String slug, StoreStatus status);

    boolean existsBySlug(String slug);
}

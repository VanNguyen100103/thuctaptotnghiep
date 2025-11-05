package com.ut.edu.backend.repository;

import com.ut.edu.backend.enums.Role;
import com.ut.edu.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    // ==================== DASHBOARD OPTIMIZATION QUERIES ====================

    /**
     * Count enabled users
     */
    Long countByEnabled(Boolean enabled);

    /**
     * Get recent users with limit
     */
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findRecentUsers(Pageable pageable);

    // ==================== USER FILTERING QUERIES (ITEM 4) ====================

    /**
     * Find users by role and enabled status with pagination
     */
    @Query("SELECT u FROM User u WHERE (:role IS NULL OR :role MEMBER OF u.roles) " +
           "AND (:enabled IS NULL OR u.enabled = :enabled) " +
           "ORDER BY u.createdAt DESC")
    Page<User> findByRoleAndEnabled(
            @Param("role") Role role,
            @Param("enabled") Boolean enabled,
            Pageable pageable);

    /**
     * Search users by username or email with filters
     */
    @Query("SELECT u FROM User u WHERE " +
           "(LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:role IS NULL OR :role MEMBER OF u.roles) " +
           "AND (:enabled IS NULL OR u.enabled = :enabled)")
    Page<User> searchUsersWithFilters(
            @Param("search") String search,
            @Param("role") Role role,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}

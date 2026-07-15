package com.ut.edu.backend.user;

import com.ut.edu.backend.security.AuthorizationService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Address Controller
 * Manages user addresses with IDOR protection
 */
@RestController
@RequestMapping("/addresses")
@Slf4j
public class AddressController {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * Get all addresses for current user
     * GET /api/addresses
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUserAddresses() {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();
            List<Address> addresses = addressRepository.findByUserId(currentUserId);

            return ResponseEntity.ok(Map.of(
                    "addresses", addresses,
                    "count", addresses.size()
            ));

        } catch (Exception e) {
            log.error("Failed to get user addresses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve addresses"));
        }
    }

    /**
     * Get address by ID (with IDOR protection)
     * GET /api/addresses/{addressId}
     */
    @GetMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAddressById(@PathVariable Long addressId) {
        try {
            // IDOR Protection: Check if current user can access this address
            authorizationService.requireAddressAccess(addressId);

            Address address = addressRepository.findById(addressId)
                    .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

            return ResponseEntity.ok(address);

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to get address: {}", addressId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve address"));
        }
    }

    /**
     * Create new address for current user
     * POST /api/addresses
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> createAddress(@Valid @RequestBody Address address) {
        try {
            Long currentUserId = authorizationService.getCurrentUserId();

            // Set the user for this address
            address.setUser(userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found")));

            // If this is set as default, unset other default addresses of the same type
            if (address.getIsDefault() != null && address.getIsDefault()) {
                List<Address> existingAddresses = addressRepository.findByUserId(currentUserId);
                for (Address existing : existingAddresses) {
                    if (existing.getType() == address.getType() && existing.getIsDefault()) {
                        existing.setIsDefault(false);
                        addressRepository.save(existing);
                    }
                }
            }

            Address savedAddress = addressRepository.save(address);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Address created successfully",
                            "address", savedAddress
                    ));

        } catch (Exception e) {
            log.error("Failed to create address", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create address"));
        }
    }

    /**
     * Update address (with IDOR protection)
     * PUT /api/addresses/{addressId}
     */
    @PutMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody Address updatedAddress) {
        try {
            // IDOR Protection: Check if current user can modify this address
            authorizationService.requireAddressAccess(addressId);

            Address existingAddress = addressRepository.findById(addressId)
                    .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

            // Update fields
            existingAddress.setAddressLine1(updatedAddress.getAddressLine1());
            existingAddress.setAddressLine2(updatedAddress.getAddressLine2());
            existingAddress.setCity(updatedAddress.getCity());
            existingAddress.setStateProvince(updatedAddress.getStateProvince());
            existingAddress.setPostalCode(updatedAddress.getPostalCode());
            existingAddress.setCountry(updatedAddress.getCountry());
            existingAddress.setType(updatedAddress.getType());

            // Handle default address flag
            if (updatedAddress.getIsDefault() != null && updatedAddress.getIsDefault()) {
                Long currentUserId = authorizationService.getCurrentUserId();
                List<Address> userAddresses = addressRepository.findByUserId(currentUserId);
                for (Address addr : userAddresses) {
                    if (addr.getType() == updatedAddress.getType() && addr.getIsDefault() && !addr.getId().equals(addressId)) {
                        addr.setIsDefault(false);
                        addressRepository.save(addr);
                    }
                }
                existingAddress.setIsDefault(true);
            } else if (updatedAddress.getIsDefault() != null) {
                existingAddress.setIsDefault(updatedAddress.getIsDefault());
            }

            Address savedAddress = addressRepository.save(existingAddress);

            return ResponseEntity.ok(Map.of(
                    "message", "Address updated successfully",
                    "address", savedAddress
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to update address: {}", addressId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update address"));
        }
    }

    /**
     * Delete address (with IDOR protection)
     * DELETE /api/addresses/{addressId}
     */
    @DeleteMapping("/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteAddress(@PathVariable Long addressId) {
        try {
            // IDOR Protection: Check if current user can delete this address
            authorizationService.requireAddressAccess(addressId);

            addressRepository.deleteById(addressId);

            return ResponseEntity.ok(Map.of(
                    "message", "Address deleted successfully"
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to delete address: {}", addressId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete address"));
        }
    }

    /**
     * Set address as default (with IDOR protection)
     * POST /api/addresses/{addressId}/set-default
     */
    @PostMapping("/{addressId}/set-default")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<?> setDefaultAddress(@PathVariable Long addressId) {
        try {
            // IDOR Protection: Check if current user can modify this address
            authorizationService.requireAddressAccess(addressId);

            Address address = addressRepository.findById(addressId)
                    .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));

            Long currentUserId = authorizationService.getCurrentUserId();

            // Unset other default addresses of the same type
            List<Address> userAddresses = addressRepository.findByUserId(currentUserId);
            for (Address addr : userAddresses) {
                if (addr.getType() == address.getType() && addr.getIsDefault() && !addr.getId().equals(addressId)) {
                    addr.setIsDefault(false);
                    addressRepository.save(addr);
                }
            }

            // Set this address as default
            address.setIsDefault(true);
            addressRepository.save(address);

            return ResponseEntity.ok(Map.of(
                    "message", "Address set as default successfully",
                    "address", address
            ));

        } catch (SecurityException e) {
            log.warn("IDOR attempt detected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to set default address: {}", addressId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to set default address"));
        }
    }
}

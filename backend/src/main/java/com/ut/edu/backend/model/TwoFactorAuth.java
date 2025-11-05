package com.ut.edu.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "two_factor_auth")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private String secret;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "backup_codes",
        joinColumns = @JoinColumn(name = "two_factor_auth_id")
    )
    @Column(name = "code")
    @Builder.Default
    private List<String> backupCodes = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime enabledAt;

    @Column
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void markAsUsed() {
        lastUsedAt = LocalDateTime.now();
    }

    public boolean isBackupCodeValid(String code) {
        return backupCodes.contains(code);
    }

    public void useBackupCode(String code) {
        backupCodes.remove(code);
        markAsUsed();
    }
}

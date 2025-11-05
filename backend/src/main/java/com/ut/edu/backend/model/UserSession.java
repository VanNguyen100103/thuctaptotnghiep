package com.ut.edu.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * User Session Model for Redis
 * Stores user session information when they login
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;

    private String accessToken;
    private String refreshToken;

    private LocalDateTime loginTime;
    private LocalDateTime lastActivityTime;

    private String ipAddress;
    private String userAgent;
    private String deviceId;

    private Boolean enabled;
    private Boolean accountNonLocked;
}

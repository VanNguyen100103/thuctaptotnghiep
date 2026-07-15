package com.ut.edu.backend.security;

import com.ut.edu.backend.user.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * UserPrincipal implementing UserDetails for Spring Security
 * Represents authenticated user in the security context
 */
@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long id;
    private String username;
    private String email;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    private boolean enabled;
    private boolean accountNonLocked;

    /**
     * Tenant link: null for regular customers and SUPER_ADMIN,
     * set for store owners/managers/staff.
     */
    private Long storeId;
    private String storeRole;

    /**
     * Create UserPrincipal from User entity
     */
    public static UserPrincipal create(User user) {
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        // Store role becomes an authority too: ROLE_OWNER / ROLE_MANAGER /
        // ROLE_STAFF (per store) and ROLE_SUPER_ADMIN (platform operator),
        // used by /store/** and /platform/** endpoints
        if (user.getStoreRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getStoreRole().name()));
        }

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                authorities,
                user.getEnabled(),
                user.getAccountNonLocked(),
                user.getStore() != null ? user.getStore().getId() : null,
                user.getStoreRole() != null ? user.getStoreRole().name() : null
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

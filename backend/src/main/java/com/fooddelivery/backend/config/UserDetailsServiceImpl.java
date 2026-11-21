package com.fooddelivery.backend.config;

import com.fooddelivery.backend.entity.Role;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserDetailsServiceImpl — the bridge between Spring Security and our DB.
 *
 * CONCEPT: Spring Security's Authentication Flow
 * ───────────────────────────────────────────────
 * Spring Security doesn't know what a "User" is in our system.
 * It works with its own type: UserDetails.
 *
 * UserDetailsService is a contract: "given a username (email),
 * load the user." Spring Security calls this during login to:
 *   1. Load the user from DB
 *   2. Compare the submitted password with the stored hash
 *   3. Build the SecurityContext if credentials match
 *
 * We implement it ourselves so Spring Security uses OUR database,
 * not some default in-memory user store.
 *
 * CONCEPT: GrantedAuthority
 * ──────────────────────────
 * Spring Security represents permissions as GrantedAuthority objects.
 * We convert our Role enum → "ROLE_CUSTOMER" string → SimpleGrantedAuthority.
 * This powers @PreAuthorize("hasRole('CUSTOMER')") checks.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        // Convert our Role enum to a Spring Security authority string.
        // Spring Security expects "ROLE_" prefix for hasRole() checks.
        String authorityStr = "ROLE_" + user.getRole().name();

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(authorityStr)))
                .disabled(!user.isActive())
                .build();
    }
}


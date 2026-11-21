package com.fooddelivery.backend.config;

import com.fooddelivery.backend.entity.Role;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminBootstrap — creates the first ADMIN on startup.
 *
 * Self-registration as ADMIN is blocked (security fix), so how does the
 * first admin exist? Set ADMIN_EMAIL / ADMIN_PASSWORD / ADMIN_PHONE as env
 * variables. On startup, if no user with that email exists, one is created.
 *
 * Edge cases:
 *   - variables not set        → do nothing (normal for dev/tests)
 *   - user already exists      → do nothing (safe on every restart)
 *   - weak password (< 12)     → refuse and log an error, never create it
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Value("${app.admin.phone:}")
    private String phone;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || phone.isBlank()) {
            return;
        }
        if (password.length() < 12) {
            log.error("[ADMIN] ADMIN_PASSWORD must be at least 12 characters — admin NOT created.");
            return;
        }
        if (userRepository.existsByEmail(email) || userRepository.existsByPhone(phone)) {
            log.info("[ADMIN] Admin account {} already exists.", email);
            return;
        }
        userRepository.save(User.builder()
                .name("Administrator")
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .build());
        log.info("[ADMIN] Created admin account {}", email);
    }
}

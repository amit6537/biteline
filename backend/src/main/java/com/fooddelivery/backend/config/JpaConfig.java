package com.fooddelivery.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JpaConfig — enables JPA Auditing for @CreatedDate / @LastModifiedDate.
 *
 * CONCEPT: JPA Auditing
 * ──────────────────────
 * @EnableJpaAuditing activates Spring Data's auditing infrastructure.
 * This tells Spring to automatically populate fields annotated with:
 *   @CreatedDate   → set once when the entity is first persisted
 *   @LastModifiedDate → updated every time the entity is saved
 *
 * Without this annotation, those fields would remain null.
 * With it, you never need to manually set timestamps — Spring handles it.
 *
 * We keep this in a separate config class (not in SecurityConfig or main app)
 * to keep responsibilities clean.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}


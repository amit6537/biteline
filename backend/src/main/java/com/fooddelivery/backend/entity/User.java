package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User entity — maps to the "users" table in PostgreSQL.
 *
 * CONCEPT: JPA Entity Mapping
 * ───────────────────────────
 * @Entity tells Hibernate: "this class is a database table."
 * @Table(name = "users") maps it to the exact table name.
 * Each field annotated with @Column maps to a column.
 *
 * WHY UUID for the ID?
 *   See schema.sql — UUIDs are safe to generate before inserting,
 *   avoid leaking row count, and work in distributed systems.
 *
 * WHY @EntityListeners(AuditingEntityListener.class)?
 *   Spring Data JPA can auto-populate @CreatedDate and
 *   @LastModifiedDate. We just need @EnableJpaAuditing on the config.
 *
 * CONCEPT: Lombok
 * ───────────────
 * @Data = @Getter + @Setter + @ToString + @EqualsAndHashCode
 * @Builder = lets us construct: User.builder().name("John").build()
 * @NoArgsConstructor + @AllArgsConstructor = both constructors
 *   (JPA needs the no-arg constructor; Builder needs all-args)
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    /**
     * WHY store password_hash and not the raw password?
     * NEVER store plain-text passwords. We use BCrypt (Phase 2):
     *   BCrypt("password") → "$2a$10$...hashed..."
     * BCrypt is one-way — you can verify but cannot reverse it.
     * Even if the DB is leaked, passwords are safe.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Role controls what a user can do in the app.
     * We use @Enumerated(STRING) so the DB stores "CUSTOMER",
     * not 0, 1, 2 — human-readable and rename-safe.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}


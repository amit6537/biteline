package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository — data access layer for the users table.
 *
 * CONCEPT: Spring Data JPA Repository
 * ─────────────────────────────────────
 * By extending JpaRepository<User, UUID>, Spring generates ALL
 * standard DB operations at startup — no SQL needed:
 *   - findById(), findAll(), save(), delete(), count(), ...
 *
 * Custom queries follow a naming convention Spring understands:
 *   findBy<FieldName>  → SELECT * FROM users WHERE field = ?
 *
 * Spring Data reads the method name and generates the query.
 * No @Query annotation needed for simple lookups.
 *
 * WHY Optional<User> instead of User?
 *   Optional forces the caller to handle the "not found" case
 *   explicitly — no surprise NullPointerExceptions.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // SELECT * FROM users WHERE phone = ?
    Optional<User> findByPhone(String phone);

    // SELECT COUNT(*) > 0 FROM users WHERE email = ?
    // Used to check duplicates during registration
    boolean existsByEmail(String email);

    // SELECT COUNT(*) > 0 FROM users WHERE phone = ?
    boolean existsByPhone(String phone);
}


package com.fooddelivery.backend.repository;

import com.fooddelivery.backend.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Revoke ONE token, but only if it is still active.
     * Returns 1 if WE revoked it, 0 if it was already revoked.
     *
     * WHY a conditional UPDATE instead of "read, check, save"?
     * Two requests with the same token at the same moment would both read
     * "active" and both get new tokens. The database runs this UPDATE one
     * at a time per row, so exactly one request gets 1, the other gets 0.
     *
     * clearAutomatically: forget cached entities afterwards, so a following
     * findById() reads the row's real, updated state from the DB.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason "
            + "WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfActive(@Param("id") UUID id, @Param("now") LocalDateTime now, @Param("reason") String reason);

    /** Revoke every still-active token of one login (one device). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason "
            + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now,
                     @Param("reason") String reason);

    /** Revoke every still-active token of a user (all devices). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now,
                         @Param("reason") String reason);

    /** Expired tokens are useless (the JWT itself is expired) → safe to delete. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

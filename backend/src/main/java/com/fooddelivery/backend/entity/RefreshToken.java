package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RefreshToken — the server-side record of one refresh token we issued.
 *
 * The token the client holds is a signed JWT whose "jti" claim = {@link #id}.
 * A JWT alone can't be "taken back" before it expires; this row can be
 * revoked, which is what makes logout and rotation possible.
 *
 * The id is set by us (not generated) because it must go inside the JWT
 * before the row is saved.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** All tokens from one login share a family. */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 20)
    private String revokeReason;
}

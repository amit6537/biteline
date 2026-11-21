package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.LoginRequest;
import com.fooddelivery.backend.dto.request.RegisterRequest;
import com.fooddelivery.backend.dto.response.AuthResponse;
import com.fooddelivery.backend.entity.User;
import com.fooddelivery.backend.entity.Role;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.exception.ConflictException;
import com.fooddelivery.backend.repository.UserRepository;
import com.fooddelivery.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService — business logic for registration and login.
 *
 * CONCEPT: Service Layer
 * ───────────────────────
 * The service layer is where business logic lives.
 * Controllers handle HTTP (request parsing, response formatting).
 * Services handle WHAT the app actually does.
 * Repositories handle HOW data is stored/retrieved.
 *
 * This separation means:
 *   - Business logic is testable without HTTP (pure Java)
 *   - Controllers stay thin and focused
 *   - Logic can be reused across controllers
 *
 * CONCEPT: @Transactional
 * ─────────────────────────
 * On register(), we need to:
 *   1. Check email/phone uniqueness
 *   2. Hash the password
 *   3. Save the user
 *
 * @Transactional wraps the whole method in a DB transaction.
 * If ANY step throws an exception → entire transaction rolls back.
 * No partial data ever gets committed.
 *
 * Best practice: put @Transactional on the service, NOT the controller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Register a new user.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 0. SECURITY: nobody may self-register as ADMIN.
        //    Without this check, anyone could POST {"role":"ADMIN"} and get
        //    full access. Admins are created directly in the database.
        Role role = request.getRole() == null ? Role.CUSTOMER : request.getRole();
        if (role == Role.ADMIN) {
            throw new BadRequestException("Cannot self-register with role ADMIN");
        }

        // 1. Check for duplicate email or phone
        //    WHY check before saving?
        //    The DB has a UNIQUE constraint too, but we check here first
        //    to give a friendly error message instead of a DataIntegrityViolationException.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email is already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new ConflictException("Phone number is already registered: " + request.getPhone());
        }

        // 2. Hash the password with BCrypt
        //    BCrypt generates a random "salt" per password and embeds it in the hash.
        //    WHY salt? So two users with the same password get DIFFERENT hashes.
        //    This defeats rainbow table attacks.
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Build and save the User entity
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(hashedPassword)
                .role(role)
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {} ({})", savedUser.getEmail(), savedUser.getRole());

        // 4. Generate tokens and return — user is immediately logged in after register
        return buildAuthResponse(savedUser);
    }

    /**
     * Login an existing user.
     */
    public AuthResponse login(LoginRequest request, String clientIp) {
        // Locked after too many wrong passwords? → 429, password not even checked
        loginAttemptService.checkAllowed(request.getEmail(), clientIp);

        // AuthenticationManager.authenticate() does two things:
        //   1. Calls UserDetailsService.loadUserByUsername(email) to load the user
        //   2. Compares submitted password with stored BCrypt hash
        //
        // If credentials are wrong → BadCredentialsException (→ 401)
        // If user is disabled    → DisabledException (→ 401)
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(request.getEmail(), clientIp);
            throw e; // still a normal 401 for the caller
        }
        loginAttemptService.recordSuccess(request.getEmail(), clientIp);

        // If we reach here, credentials are valid — load user to build response
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Exchange a refresh token for a new access token + a new refresh token.
     * The old refresh token stops working (rotation, see RefreshTokenService).
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return buildAuthResponse(rotation.user(), rotation.refreshToken());
    }

    /** Log out this device: its refresh token (and the tokens before it) stop working. */
    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }

    /** Log out every device of the current user. */
    public void logoutAll(User user) {
        int revoked = refreshTokenService.logoutAll(user.getId());
        log.info("User {} logged out everywhere ({} session token(s) revoked)", user.getEmail(), revoked);
    }

    /** Login / register: a brand-new session. */
    private AuthResponse buildAuthResponse(User user) {
        return buildAuthResponse(user, refreshTokenService.issueForNewLogin(user));
    }

    /**
     * Shared helper — generates the access token and builds the response DTO.
     */
    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpiryMs() / 1000) // convert ms → seconds
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}


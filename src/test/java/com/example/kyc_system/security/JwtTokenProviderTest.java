package com.example.kyc_system.security;

// import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
// import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 *
 * Tests cover:
 * - Token generation from Authentication + tenantId
 * - Token generation from username only (refresh flow)
 * - Username (subject) extraction
 * - TenantId claim extraction
 * - Token validation: valid, expired, tampered
 */
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // Use a fresh 256-bit key for all tests
    private static final String TEST_SECRET;
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    static {
        Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        TEST_SECRET = Encoders.BASE64.encode(key.getEncoded());
    }

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationDate", EXPIRATION_MS);
    }

    private Authentication mockAuthentication(String username) {
        return new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ─── generateToken() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken(Authentication, tenantId)")
    class GenerateTokenTests {

        @Test
        @DisplayName("Should generate a non-blank JWT string")
        void generateToken_ValidInput_ReturnsNonBlankToken() {
            Authentication auth = mockAuthentication("user@example.com");
            String token = jwtTokenProvider.generateToken(auth, "default");

            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("Generated token should have 3 parts (header.payload.signature)")
        void generateToken_HasThreeParts() {
            Authentication auth = mockAuthentication("user@example.com");
            String token = jwtTokenProvider.generateToken(auth, "default");

            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT must have exactly 3 dot-separated parts");
        }

        @Test
        @DisplayName("Username extracted from generated token should match Authentication name")
        void generateToken_UsernameRoundTrip() {
            Authentication auth = mockAuthentication("user@example.com");
            String token = jwtTokenProvider.generateToken(auth, "default");

            assertEquals("user@example.com", jwtTokenProvider.getUsername(token));
        }

        @Test
        @DisplayName("TenantId embedded in generated token should match input tenantId")
        void generateToken_TenantIdRoundTrip() {
            Authentication auth = mockAuthentication("user@example.com");
            String token = jwtTokenProvider.generateToken(auth, "tenant-abc");

            assertEquals("tenant-abc", jwtTokenProvider.getTenantIdFromToken(token));
        }

        @Test
        @DisplayName("Different users should produce different tokens")
        void generateToken_DifferentUsers_DifferentTokens() {
            String token1 = jwtTokenProvider.generateToken(mockAuthentication("alice@test.com"), "default");
            String token2 = jwtTokenProvider.generateToken(mockAuthentication("bob@test.com"), "default");

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Different tenants should produce different tokens for same user")
        void generateToken_DifferentTenants_DifferentTokens() {
            Authentication auth = mockAuthentication("user@test.com");
            String token1 = jwtTokenProvider.generateToken(auth, "tenant-A");
            String token2 = jwtTokenProvider.generateToken(auth, "tenant-B");

            assertNotEquals(token1, token2);
        }
    }

    // ─── generateTokenFromUsername() ──────────────────────────────────────────

    @Nested
    @DisplayName("generateTokenFromUsername(username)")
    class GenerateTokenFromUsernameTests {

        @Test
        @DisplayName("Should generate valid token from username string")
        void generateTokenFromUsername_ValidUsername_ReturnsToken() {
            String token = jwtTokenProvider.generateTokenFromUsername("refresh@example.com");

            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("Username extracted from token should match input")
        void generateTokenFromUsername_UsernameRoundTrip() {
            String username = "refresh@example.com";
            String token = jwtTokenProvider.generateTokenFromUsername(username);

            assertEquals(username, jwtTokenProvider.getUsername(token));
        }

        @Test
        @DisplayName("Token generated from username should not contain tenantId claim")
        void generateTokenFromUsername_NoTenantIdClaim() {
            String token = jwtTokenProvider.generateTokenFromUsername("user@test.com");

            // getTenantIdFromToken returns null when claim is absent
            assertNull(jwtTokenProvider.getTenantIdFromToken(token));
        }
    }

    // ─── validateToken() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken()")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return true for a fresh valid token")
        void validateToken_ValidToken_ReturnsTrue() {
            String token = jwtTokenProvider.generateTokenFromUsername("user@test.com");
            assertTrue(jwtTokenProvider.validateToken(token));
        }

        @Test
        @DisplayName("Should return false for an already-expired token")
        void validateToken_ExpiredToken_ReturnsFalse() {
            // Generate a token that expired 1 second ago
            JwtTokenProvider expiredProvider = new JwtTokenProvider();
            ReflectionTestUtils.setField(expiredProvider, "jwtSecret", TEST_SECRET);
            ReflectionTestUtils.setField(expiredProvider, "jwtExpirationDate", -1000L); // already expired

            String expiredToken = expiredProvider.generateTokenFromUsername("user@test.com");
            assertFalse(jwtTokenProvider.validateToken(expiredToken));
        }

        @Test
        @DisplayName("Should return false for a token signed with a different key (tampered)")
        void validateToken_TamperedSignature_ReturnsFalse() {
            // Different secret = different signing key
            Key anotherKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            String anotherSecret = Encoders.BASE64.encode(anotherKey.getEncoded());

            JwtTokenProvider otherProvider = new JwtTokenProvider();
            ReflectionTestUtils.setField(otherProvider, "jwtSecret", anotherSecret);
            ReflectionTestUtils.setField(otherProvider, "jwtExpirationDate", EXPIRATION_MS);

            String tokenFromOtherKey = otherProvider.generateTokenFromUsername("user@test.com");

            assertFalse(jwtTokenProvider.validateToken(tokenFromOtherKey),
                    "Token signed with wrong key must fail validation");
        }

        @Test
        @DisplayName("Should return false for a malformed token string")
        void validateToken_MalformedToken_ReturnsFalse() {
            assertFalse(jwtTokenProvider.validateToken("not.a.valid.jwt"));
        }

        @Test
        @DisplayName("Should return false for an empty string")
        void validateToken_EmptyString_ReturnsFalse() {
            assertFalse(jwtTokenProvider.validateToken(""));
        }

        @Test
        @DisplayName("Should return false for a null token")
        void validateToken_NullToken_ReturnsFalse() {
            assertFalse(jwtTokenProvider.validateToken(null));
        }
    }

    // ─── getUsername() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUsername()")
    class GetUsernameTests {

        @Test
        @DisplayName("Should extract correct username from valid token")
        void getUsername_ValidToken_ReturnsCorrectUsername() {
            String token = jwtTokenProvider.generateTokenFromUsername("alice@company.com");
            assertEquals("alice@company.com", jwtTokenProvider.getUsername(token));
        }
    }

    // ─── getTenantIdFromToken() ───────────────────────────────────────────────

    @Nested
    @DisplayName("getTenantIdFromToken()")
    class GetTenantIdTests {

        @Test
        @DisplayName("Should return correct tenantId from token generated with tenantId")
        void getTenantIdFromToken_WithTenantClaim_ReturnsCorrectId() {
            Authentication auth = mockAuthentication("user@test.com");
            String token = jwtTokenProvider.generateToken(auth, "hdfc-tenant");

            assertEquals("hdfc-tenant", jwtTokenProvider.getTenantIdFromToken(token));
        }

        @Test
        @DisplayName("Should return null when token has no tenantId claim")
        void getTenantIdFromToken_NoClaim_ReturnsNull() {
            String token = jwtTokenProvider.generateTokenFromUsername("user@test.com");
            assertNull(jwtTokenProvider.getTenantIdFromToken(token));
        }
    }

    // ─── getExpirationRemaining() ─────────────────────────────────────────────

    @Nested
    @DisplayName("getExpirationRemaining()")
    class GetExpirationRemainingTests {

        @Test
        @DisplayName("Should return positive remaining time for a fresh token")
        void getExpirationRemaining_ValidToken_ReturnsPositiveValue() {
            String token = jwtTokenProvider.generateTokenFromUsername("user@test.com");
            long remaining = jwtTokenProvider.getExpirationRemaining(token);

            assertTrue(remaining > 0, "Remaining time should be positive");
            assertTrue(remaining <= EXPIRATION_MS, "Remaining time should not exceed initial expiration");
        }
    }
}
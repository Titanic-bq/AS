package ee.ahjusepad.store;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Haldab administraatorite autentimist, õiguseid, piiratud sessioone ja
 * ebaõnnestunud sisselogimiste ajutist piirangut.
 */
final class AdminAuth {
    static final String COOKIE_NAME = "AHJ_ADMIN_SESSION";
    static final int SESSION_MAX_AGE_SECONDS = 30 * 60;
    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_TRACKED_ADDRESSES = 10_000;
    private static final int MAX_SESSIONS = 1_000;
    private static final int MAX_USERS = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, UserCredential> users = new HashMap<>();
    private final Set<String> normalizedUsernames = new HashSet<>();
    private final byte[] dummySalt = randomBytes(16);
    private final byte[] dummyHash = createDummyHash();
    private final boolean cookieSecure;
    private final Map<String, LoginFailures> failuresByAddress = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();
    private long lastFailureCleanup;

    AdminAuth(List<ConfiguredUser> configuredUsers, boolean cookieSecure) {
        if (configuredUsers == null || configuredUsers.isEmpty() || configuredUsers.size() > MAX_USERS) {
            throw new IllegalArgumentException("Configure between 1 and " + MAX_USERS + " admin users");
        }
        for (ConfiguredUser configured : configuredUsers) {
            String username = configured.username();
            String password = configured.password();
            if (username == null || username.isBlank() || username.length() > 100
                    || username.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "Admin usernames must contain 1–100 characters and no control characters");
            }
            if (password == null || password.length() < 16 || password.length() > 256
                    || password.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "Admin passwords must contain 16–256 characters and no control characters");
            }
            Role role;
            try {
                role = Role.valueOf(configured.role());
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IllegalArgumentException("Admin role must be ADMIN or VIEWER", ex);
            }
            String normalizedUsername = username.toLowerCase(Locale.ROOT);
            if (!normalizedUsernames.add(normalizedUsername)) {
                throw new IllegalArgumentException("Admin usernames must be unique, ignoring case");
            }
            byte[] salt = randomBytes(16);
            char[] passwordChars = password.toCharArray();
            byte[] hash;
            try {
                hash = hashPassword(passwordChars, salt);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
            users.put(username, new UserCredential(username, role, salt, hash));
        }
        this.cookieSecure = cookieSecure;
    }

    boolean cookieSecure() {
        return cookieSecure;
    }

    synchronized String login(String suppliedUsername, String suppliedPassword, String remoteAddress) {
        long now = System.currentTimeMillis();
        LoginFailures failures = failuresFor(remoteAddress, now);
        if (failures.count >= MAX_LOGIN_FAILURES) {
            throw new LoginRateLimited();
        }

        String safeUsername = suppliedUsername == null ? "" : suppliedUsername;
        char[] suppliedPasswordChars = suppliedPassword == null
                ? new char[0] : suppliedPassword.toCharArray();
        UserCredential credential = users.get(safeUsername);
        byte[] salt = credential == null ? dummySalt : credential.salt;
        byte[] expectedHash = credential == null ? dummyHash : credential.passwordHash;
        byte[] suppliedPasswordHash;
        try {
            suppliedPasswordHash = hashPassword(suppliedPasswordChars, salt);
        } finally {
            Arrays.fill(suppliedPasswordChars, '\0');
        }

        boolean passwordMatches = MessageDigest.isEqual(expectedHash, suppliedPasswordHash);
        Arrays.fill(suppliedPasswordHash, (byte) 0);
        if (credential == null || !passwordMatches) {
            failures.count++;
            throw new InvalidCredentials();
        }

        failuresByAddress.remove(remoteAddress);
        removeExpiredSessions(now);
        if (sessions.size() >= MAX_SESSIONS) {
            throw new SessionLimitReached();
        }
        String token = newToken();
        sessions.put(token, new Session(credential.username, credential.role, newToken(),
                now + SESSION_MAX_AGE_SECONDS * 1000L));
        return token;
    }

    synchronized SessionInfo session(String token) {
        Session session = sessions.get(token);
        if (session == null) return null;
        long now = System.currentTimeMillis();
        if (session.expiresAt <= now) {
            sessions.remove(token);
            return null;
        }
        Session renewed = new Session(session.username, session.role, session.csrfToken,
                now + SESSION_MAX_AGE_SECONDS * 1000L);
        sessions.put(token, renewed);
        return new SessionInfo(renewed.username, renewed.role.name(), renewed.csrfToken);
    }

    synchronized boolean logout(String token) {
        return sessions.remove(token) != null;
    }

    private LoginFailures failuresFor(String address, long now) {
        if (now - lastFailureCleanup >= 60_000L) {
            failuresByAddress.entrySet().removeIf(entry ->
                    now - entry.getValue().windowStartedAt >= LOGIN_WINDOW_MILLIS);
            lastFailureCleanup = now;
        }
        LoginFailures failures = failuresByAddress.get(address);
        if (failures != null && now - failures.windowStartedAt >= LOGIN_WINDOW_MILLIS) {
            failuresByAddress.remove(address);
            failures = null;
        }
        if (failures == null) {
            if (failuresByAddress.size() >= MAX_TRACKED_ADDRESSES) {
                throw new LoginRateLimited();
            }
            failures = new LoginFailures(now);
            failuresByAddress.put(address, failures);
        }
        return failures;
    }

    private void removeExpiredSessions(long now) {
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private static String newToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
    }

    private static byte[] createDummyHash() {
        char[] password = "invalid-account".toCharArray();
        try {
            return hashPassword(password, randomBytes(16));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static byte[] hashPassword(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PASSWORD_ITERATIONS, PASSWORD_KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Password hashing is unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }

    record ConfiguredUser(String username, String password, String role) {}
    record SessionInfo(String username, String role, String csrfToken) {}

    private enum Role {
        ADMIN,
        VIEWER
    }

    private static final class UserCredential {
        private final String username;
        private final Role role;
        private final byte[] salt;
        private final byte[] passwordHash;

        private UserCredential(String username, Role role, byte[] salt, byte[] passwordHash) {
            this.username = username;
            this.role = role;
            this.salt = salt;
            this.passwordHash = passwordHash;
        }
    }

    private static final class LoginFailures {
        private final long windowStartedAt;
        private int count;

        private LoginFailures(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }

    private record Session(String username, Role role, String csrfToken, long expiresAt) {}

    static final class InvalidCredentials extends RuntimeException {}
    static final class LoginRateLimited extends RuntimeException {}
    static final class SessionLimitReached extends RuntimeException {}
}

package ru.lopon.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lopon.auth.AuthDtos.LoginRequest;
import ru.lopon.auth.AuthDtos.RegisterRequest;
import ru.lopon.auth.AuthDtos.Session;
import ru.lopon.auth.AuthDtos.UserResponse;
import ru.lopon.config.LoponProperties;
import ru.lopon.web.ApiException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String BAD_CREDENTIALS = "Неверный email или пароль";
    private static final String SESSION_EXPIRED = "Сессия закончилась — войди заново";

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final LoponProperties.Auth auth;
    private final String dummyHash;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
                       PasswordEncoder passwordEncoder, LoponProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.auth = properties.auth();
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Session register(RegisterRequest request) {
        if (!auth.registrationEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Регистрация закрыта");
        }
        requireBcryptLength(request.password());
        String email = normalize(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Этот email уже зарегистрирован");
        }
        String name = request.displayName() == null || request.displayName().isBlank()
                ? email.substring(0, email.indexOf('@')) : request.displayName().trim();
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password()), name));
        log.atInfo().addKeyValue("user_id", user.getId()).log("user registered");
        return tokens.issue(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(LoginRequest request) {
        AppUser user = users.findByEmail(normalize(request.email())).orElse(null);
        if (user == null || !fitsBcrypt(request.password())) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw unauthorized();
        }
        Instant now = Instant.now();
        boolean matches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (user.isLocked(now) || !matches) {
            if (!user.isLocked(now)) {
                user.loginFailed(auth.maxFailedLogins(), now.plus(auth.lockDuration()));
            }
            log.atWarn().addKeyValue("user_id", user.getId()).addKeyValue("locked", user.isLocked(now)).log("login failed");
            throw unauthorized();
        }
        user.loginSucceeded();
        return tokens.issue(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
        }
        String hash = TokenService.hash(rawToken);
        Instant now = Instant.now();
        if (refreshTokens.revokeIfActive(hash, now) == 1) {
            RefreshToken old = refreshTokens.findByTokenHash(hash).orElseThrow();
            AppUser user = users.findById(old.getUserId()).orElseThrow();
            Session session = tokens.issue(user, old.getFamilyId());
            old.replacedBy(session.refreshTokenId());
            return session;
        }
        refreshTokens.findByTokenHash(hash)
                .filter(t -> t.getRevokedAt() != null && t.getRevokedAt().isBefore(now.minus(auth.refreshReuseGrace())))
                .ifPresent(t -> {
                    refreshTokens.revokeFamily(t.getFamilyId(), now);
                    log.atWarn().addKeyValue("user_id", t.getUserId()).log("refresh token reuse detected, family revoked");
                });
        throw new ApiException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokens.findByTokenHash(TokenService.hash(rawToken))
                    .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(long userId) {
        return UserResponse.of(user(userId));
    }

    @Transactional
    public Session changePassword(long userId, String currentPassword, String newPassword) {
        AppUser user = user(userId);
        requirePassword(user, currentPassword);
        requireBcryptLength(newPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllOfUser(userId, Instant.now());
        log.atInfo().addKeyValue("user_id", userId).log("password changed, sessions revoked");
        return tokens.issue(users.findById(userId).orElseThrow(), UUID.randomUUID());
    }

    @Transactional
    public void deleteAccount(long userId, String password) {
        AppUser user = user(userId);
        requirePassword(user, password);
        users.delete(user);
        log.atInfo().addKeyValue("user_id", userId).log("account deleted");
    }

    private AppUser user(long userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED));
    }

    private void requirePassword(AppUser user, String password) {
        if (!fitsBcrypt(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Неверный текущий пароль");
        }
    }

    private static void requireBcryptLength(String password) {
        if (!fitsBcrypt(password)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Пароль длиннее 72 байт в UTF-8 — укороти его");
        }
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
    }
}

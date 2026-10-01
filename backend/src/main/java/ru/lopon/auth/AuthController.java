package ru.lopon.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.lopon.auth.AuthDtos.DeleteAccountRequest;
import ru.lopon.auth.AuthDtos.LoginRequest;
import ru.lopon.auth.AuthDtos.PasswordChangeRequest;
import ru.lopon.auth.AuthDtos.RegisterRequest;
import ru.lopon.auth.AuthDtos.Session;
import ru.lopon.auth.AuthDtos.TokenResponse;
import ru.lopon.auth.AuthDtos.UserResponse;
import ru.lopon.config.OpenApiConfig;
import ru.lopon.web.ApiException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Авторизация", description = "Регистрация, вход по Bearer JWT, refresh-токен в HttpOnly-cookie")
public class AuthController {

    private static final Set<String> SAME_SITE_FETCH = Set.of("same-origin", "none");

    private final AuthService auth;
    private final RefreshCookie cookie;

    public AuthController(AuthService auth, RefreshCookie cookie) {
        this.auth = auth;
        this.cookie = cookie;
    }

    @Operation(summary = "Регистрация: создаёт пользователя и сразу выдаёт токены")
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  @RequestHeader(value = "Sec-Fetch-Site", required = false) String site) {
        requireSameSite(site);
        return withCookie(HttpStatus.CREATED, auth.register(request));
    }

    @Operation(summary = "Вход: access-токен в теле, refresh-токен в HttpOnly-cookie")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               @RequestHeader(value = "Sec-Fetch-Site", required = false) String site) {
        requireSameSite(site);
        return withCookie(HttpStatus.OK, auth.login(request));
    }

    @Operation(summary = "Новый access-токен по refresh-cookie; старый refresh отзывается (ротация)")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String token,
                                                 @RequestHeader(value = "Sec-Fetch-Site", required = false) String site) {
        requireSameSite(site);
        return withCookie(HttpStatus.OK, auth.refresh(token));
    }

    @Operation(summary = "Выход: отзывает семейство refresh-токенов и очищает cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token,
                                       @RequestHeader(value = "Sec-Fetch-Site", required = false) String site) {
        requireSameSite(site);
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
    }

    @Operation(summary = "Текущий пользователь", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @GetMapping("/me")
    public UserResponse me(CurrentUser user) {
        return auth.me(user.id());
    }

    @Operation(summary = "Смена пароля: остальные сессии отзываются", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @PostMapping("/password")
    public ResponseEntity<TokenResponse> changePassword(CurrentUser user, @Valid @RequestBody PasswordChangeRequest request) {
        return withCookie(HttpStatus.OK, auth.changePassword(user.id(), request.currentPassword(), request.newPassword()));
    }

    @Operation(summary = "Удаление аккаунта вместе со всеми тренировками", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(CurrentUser user, @Valid @RequestBody DeleteAccountRequest request) {
        auth.deleteAccount(user.id(), request.password());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(HttpStatus status, Session session) {
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.issue(session.refreshToken())).body(session.tokens());
    }

    private static void requireSameSite(String site) {
        if (site != null && !SAME_SITE_FETCH.contains(site)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Запрос с чужого сайта");
        }
    }
}

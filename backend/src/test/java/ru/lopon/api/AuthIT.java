package ru.lopon.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.lopon.support.IntegrationTest;

class AuthIT extends IntegrationTest {

    @Test
    void registerIssuesBearerTokenAndHttpOnlyRefreshCookie() {
        MvcTestResult result = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  New.Rider@Lopon.test \",\"password\":\"" + PASSWORD + "\",\"display_name\":\"Новичок\"}")
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(json(result).get("token_type").asString()).isEqualTo("Bearer");
        assertThat(json(result).get("user").get("email").asString()).isEqualTo("new.rider@lopon.test");
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("lopon_refresh=", "HttpOnly", "SameSite=Strict", "Path=/api/auth", "Secure");
        assertThat(result.getResponse().getContentAsByteArray()).asString().doesNotContain("refresh_token");
    }

    @Test
    void duplicateEmailAndWeakPasswordAreRejected() {
        Account account = register();
        assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + account.email().toUpperCase() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"short@lopon.test\",\"password\":\"1234567\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"long@lopon.test\",\"password\":\"" + "пароль".repeat(10) + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void protectedEndpointsRequireBearerToken() {
        Account account = register();
        assertThat(mvc.get().uri("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED).headers().containsHeader("WWW-Authenticate");
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization", "Bearer garbage")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/auth/me").header("Authorization", account.bearer())).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.email").isEqualTo(account.email());
    }

    @Test
    void accountIsLockedAfterRepeatedFailures() {
        Account account = register();
        for (int i = 0; i < 3; i++) {
            assertThat(login(account.email(), "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);
        }
        assertThat(login(account.email(), PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login("nobody@lopon.test", PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheFamily() {
        Account account = register();
        MvcTestResult first = refresh(account.refreshToken());
        assertThat(first).hasStatus(HttpStatus.OK);
        String rotated = first.getResponse().getCookie("lopon_refresh").getValue();
        assertThat(rotated).isNotEqualTo(account.refreshToken());
        assertThat(refresh(account.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(rotated)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefreshTokenAndCrossSiteRequestsAreRejected() {
        Account account = register();
        assertThat(mvc.post().uri("/api/auth/refresh").cookie(new Cookie("lopon_refresh", account.refreshToken()))
                .header("Sec-Fetch-Site", "cross-site")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/auth/logout").cookie(new Cookie("lopon_refresh", account.refreshToken())))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(refresh(account.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordChangeRevokesOldSessionsAndAccountCanBeDeleted() {
        Account account = register();
        assertThat(mvc.post().uri("/api/auth/password").header("Authorization", account.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"current_password\":\"" + PASSWORD + "\",\"new_password\":\"another-long-pass\"}"))
                .hasStatus(HttpStatus.OK);
        assertThat(refresh(account.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(account.email(), PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(account.email(), "another-long-pass")).hasStatus(HttpStatus.OK);
        assertThat(mvc.delete().uri("/api/auth/me").header("Authorization", account.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"another-long-pass\"}")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(login(account.email(), "another-long-pass")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}").exchange();
    }

    private MvcTestResult refresh(String token) {
        return mvc.post().uri("/api/auth/refresh").cookie(new Cookie("lopon_refresh", token)).exchange();
    }
}

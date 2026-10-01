package ru.lopon.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import ru.lopon.config.LoponProperties;

@Component
public class RefreshCookie {

    public static final String NAME = "lopon_refresh";
    private static final String PATH = "/api/auth";

    private final LoponProperties properties;

    public RefreshCookie(LoponProperties properties) {
        this.properties = properties;
    }

    public String issue(String token) {
        return build(token, properties.jwt().refreshTtl());
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.auth().cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}

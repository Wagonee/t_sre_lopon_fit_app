package ru.lopon.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("lopon")
public record LoponProperties(@PositiveOrZero int analysisConcurrency, @Valid @NotNull Jwt jwt, @Valid @NotNull Auth auth) {

    public int effectiveAnalysisConcurrency() {
        return analysisConcurrency > 0 ? analysisConcurrency : Runtime.getRuntime().availableProcessors();
    }

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl) {

        public byte[] key() {
            return Base64.getDecoder().decode(secret);
        }

        @JsonIgnore
        @AssertTrue(message = "JWT_SECRET должен быть base64 и не короче 32 байт: openssl rand -base64 32")
        public boolean isSecretStrong() {
            try {
                return secret != null && key().length >= 32;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    public record Auth(
            boolean registrationEnabled,
            boolean cookieSecure,
            @Min(1) int maxFailedLogins,
            @NotNull Duration lockDuration,
            @NotNull Duration refreshReuseGrace) {
    }
}

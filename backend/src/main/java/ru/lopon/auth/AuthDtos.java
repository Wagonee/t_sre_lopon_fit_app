package ru.lopon.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 8, max = 72) String password,
            @Size(max = 100) String displayName) {

        public RegisterRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotNull @Size(min = 8, max = 72) String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank String password) {
    }

    public record UserResponse(Long id, String email, String displayName, Instant createdAt) {

        static UserResponse of(AppUser user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }

    public record Session(TokenResponse tokens, String refreshToken, Long refreshTokenId) {
    }
}

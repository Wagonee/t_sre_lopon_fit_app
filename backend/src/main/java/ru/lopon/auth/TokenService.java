package ru.lopon.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import ru.lopon.auth.AuthDtos.Session;
import ru.lopon.auth.AuthDtos.TokenResponse;
import ru.lopon.auth.AuthDtos.UserResponse;
import ru.lopon.config.LoponProperties;

@Service
public class TokenService {

    private final SecureRandom random = new SecureRandom();
    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final LoponProperties.Jwt jwt;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, LoponProperties properties) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.jwt = properties.jwt();
    }

    public Session issue(AppUser user, UUID familyId) {
        String raw = randomToken();
        RefreshToken stored = refreshTokens.save(new RefreshToken(user.getId(), familyId, hash(raw), Instant.now().plus(jwt.refreshTtl())));
        TokenResponse tokens = new TokenResponse(accessToken(user), "Bearer", jwt.accessTtl().toSeconds(), UserResponse.of(user));
        return new Session(tokens, raw, stored.getId());
    }

    public String accessToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .audience(List.of(jwt.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.accessTtl()))
                .claim("email", user.getEmail())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

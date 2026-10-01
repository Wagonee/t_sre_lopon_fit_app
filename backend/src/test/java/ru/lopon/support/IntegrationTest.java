package ru.lopon.support;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

@SpringBootTest(properties = {
        "lopon.jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNA==",
        "lopon.auth.refresh-reuse-grace=0s",
        "lopon.auth.max-failed-logins=3"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String PASSWORD = "correct-horse-battery";
    private static final Database DB = Database.start();

    @Autowired
    protected MockMvcTester mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DB.properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    public static Map<String, String> databaseProperties() {
        return DB.properties();
    }

    protected Account register() {
        String email = "rider-" + UUID.randomUUID() + "@lopon.test";
        MvcTestResult result = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}").exchange();
        return new Account(email, json(result).get("access_token").asString(), result.getResponse().getCookie("lopon_refresh").getValue());
    }

    protected MvcTestResult upload(Account account, String fit) {
        return mvc.post().uri("/api/workouts").multipart().file(new MockMultipartFile("file", fit + ".fit", "application/octet-stream", Fixtures.fit(fit)))
                .header("Authorization", account.bearer()).exchange();
    }

    protected static JsonNode json(MvcTestResult result) {
        try {
            return Fixtures.MAPPER.readTree(result.getResponse().getContentAsString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Account(String email, String accessToken, String refreshToken) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private record Database(Map<String, String> properties) {

        static Database start() {
            String schema = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String external = System.getenv("TEST_DB_URL");
            String url;
            String username;
            String password;
            if (external != null && !external.isBlank()) {
                url = external;
                username = System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres");
                password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres");
            } else {
                PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
                postgres.start();
                url = postgres.getJdbcUrl();
                username = postgres.getUsername();
                password = postgres.getPassword();
            }
            String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
            return new Database(Map.of(
                    "spring.datasource.url", schemaUrl,
                    "spring.datasource.username", username,
                    "spring.datasource.password", password,
                    "spring.flyway.schemas", schema,
                    "spring.flyway.default-schema", schema,
                    "spring.jpa.properties.hibernate.default_schema", schema));
        }
    }
}

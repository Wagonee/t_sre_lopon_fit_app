package ru.lopon.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

public final class Fixtures {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static final List<String> FITS =
            List.of("synthetic_outdoor", "synthetic_trainer", "edge_power", "edge_hilly", "edge_short_indoor");

    private Fixtures() {
    }

    public static byte[] fit(String name) {
        return bytes("/fit/" + name + ".fit");
    }

    public static JsonNode golden(String name) {
        String path = "/golden/" + name;
        try (InputStream in = open(path)) {
            return MAPPER.readTree(path.endsWith(".gz") ? new GZIPInputStream(in) : in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] bytes(String path) {
        try (InputStream in = open(path)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream open(String path) {
        InputStream in = Fixtures.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalArgumentException("missing fixture " + path);
        }
        return in;
    }
}

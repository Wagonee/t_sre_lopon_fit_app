package ru.lopon.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import ru.lopon.support.Fixtures;
import ru.lopon.support.JsonAssert;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class FitParserGoldenTest {

    static final List<String> FITS = Fixtures.FITS;

    @ParameterizedTest
    @FieldSource("FITS")
    void recordsAndSessionMatchFitparse(String name) {
        ParsedFit parsed = FitParser.parse(Fixtures.fit(name));
        JsonNode expected = Fixtures.golden(name + ".records.json.gz");
        ObjectNode actual = Fixtures.MAPPER.createObjectNode();
        ObjectNode session = Fixtures.MAPPER.valueToTree(parsed.session());
        session.properties().removeIf(e -> e.getValue().isNull());
        actual.set("session", session);
        actual.set("records", Fixtures.MAPPER.valueToTree(parsed.records().stream().map(FitParserGoldenTest::view).toList()));
        JsonAssert.assertSimilar(expected, actual, Set.of());
    }

    @Test
    void rejectsNonFitBytes() {
        assertThatThrownBy(() -> FitParser.parse("not a fit file".getBytes()))
                .isInstanceOf(FitParseException.class);
    }

    @Test
    void readsManufacturerFromFileId() {
        assertThat(FitParser.parse(Fixtures.fit("synthetic_trainer")).manufacturer()).isEqualTo("development");
    }

    private static Map<String, Object> view(FitRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", r.timestamp());
        m.put("heart_rate", r.heartRate());
        m.put("speed", r.speed());
        m.put("altitude", r.altitude());
        m.put("cadence", r.cadence());
        m.put("power", r.power());
        m.put("temperature", r.temperature());
        m.put("distance", r.distance());
        m.put("position_lat", r.positionLat());
        m.put("position_long", r.positionLong());
        return m;
    }
}

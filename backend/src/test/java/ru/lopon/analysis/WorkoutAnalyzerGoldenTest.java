package ru.lopon.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.lopon.fit.FitParser;
import ru.lopon.support.Fixtures;
import ru.lopon.support.JsonAssert;

class WorkoutAnalyzerGoldenTest {

    private static final Set<String> JAVA_ONLY = Set.of("/meta/device", "/series/lat", "/series/lon");

    static Stream<Arguments> cases() {
        return Fixtures.FITS.stream().flatMap(f -> Stream.of(Arguments.of(f, "default"), Arguments.of(f, "custom")));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("cases")
    void matchesPythonReference(String fit, String variant) {
        AnalysisSettings settings = "custom".equals(variant)
                ? SettingsMerger.mergeWithDefaults(Fixtures.MAPPER, Fixtures.golden("settings.custom.json"))
                : AnalysisSettings.DEFAULTS;
        AnalysisResult result = WorkoutAnalyzer.analyze(FitParser.parse(Fixtures.fit(fit)), settings, fit + ".fit");
        JsonAssert.assertSimilar(Fixtures.golden(fit + "." + variant + ".json"), Fixtures.MAPPER.valueToTree(result), JAVA_ONLY);
    }

    @Test
    void outdoorDriftUsesTheFlatSegment() {
        AnalysisResult r = analyze("synthetic_outdoor");
        assertThat(r.decoupling().method()).isEqualTo("flat_segment");
        assertThat(r.decoupling().segment().minutes()).isBetween(28, 30);
        assertThat(r.decoupling().pct()).isCloseTo(0.0, within(1.0));
    }

    @Test
    void trainerHasFourErgBlocksAt170Watts() {
        AnalysisResult r = analyze("synthetic_trainer");
        assertThat(r.power().ergBlocks()).hasSize(4).allSatisfy(b -> {
            assertThat(b.minutes()).isCloseTo(4.4, within(0.2));
            assertThat(b.avgPower()).isBetween(165L, 175L);
        });
    }

    @Test
    void heartRatePeakAtHighSpeedIsReportedAsStrapArtifact() {
        assertThat(analyze("edge_power").warnings()).anyMatch(w -> w.contains("артефакт ремня"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MAGENE_FIT_PATH", matches = ".+")
    void realMageneRideIsParsedAndStrapArtifactIsReported() throws IOException {
        Path path = Path.of(System.getenv("MAGENE_FIT_PATH"));
        AnalysisResult r = WorkoutAnalyzer.analyze(FitParser.parse(Files.readAllBytes(path)), AnalysisSettings.DEFAULTS,
                path.getFileName().toString());
        assertThat(r.summary().distanceKm()).isPositive();
        assertThat(r.warnings()).anyMatch(w -> w.contains("артефакт ремня"));
    }

    private static AnalysisResult analyze(String fit) {
        return WorkoutAnalyzer.analyze(FitParser.parse(Fixtures.fit(fit)), AnalysisSettings.DEFAULTS, fit + ".fit");
    }
}

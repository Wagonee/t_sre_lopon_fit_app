package ru.lopon.analysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalysisSettings(
        @NotNull @Valid Athlete athlete,
        @NotNull @Valid HrZones hrZones,
        @NotNull @Valid Z2Band z2Band,
        @NotNull @Valid Analysis analysis,
        @NotNull @Valid Power power,
        @NotNull @Valid Temperature temperature,
        @NotNull @Valid Time time) {

    public static final AnalysisSettings DEFAULTS = new AnalysisSettings(
            new Athlete("", 70.0, 187, 195, 73, 175, "settings", 3.0),
            new HrZones("auto", List.of(0.60, 0.75, 0.82, 0.89), List.of(117.0, 146.0, 160.0, 174.0),
                    List.of("Z1 восстановление", "Z2 выносливость", "Z3 темп", "Z4 порог", "Z5 VO2max"),
                    List.of(1.0, 2.0, 3.0, 4.0, 4.0)),
            new Z2Band(135, 145),
            new Analysis(158, 75, 3.0, 15, 20, 20, 3.0, 10.0, 10, 5.0, 2.0, 45.0, 1500),
            new Power(List.of(0.55, 0.75, 0.90, 1.05, 1.20, 1.50), 120, 0.80),
            new Temperature(5.0, 25.0),
            new Time(true, 3));

    public record Athlete(
            @Size(max = 100) String name,
            @Positive @Max(500) double weightKg,
            @Positive @Max(260) double heightCm,
            @Min(100) @Max(250) double hrMax,
            @Positive @Max(150) double hrRest,
            @Positive @Max(2500) double ftp,
            @NotNull @Pattern(regexp = "settings|fit") String ftpSource,
            @Positive @Max(10) double targetWkg) {
    }

    public record HrZones(
            @NotNull @Pattern(regexp = "auto|manual") String mode,
            @NotNull @Size(min = 4, max = 4) List<@NotNull @Positive @DecimalMax("1.5") Double> pct,
            @NotNull @Size(min = 4, max = 4) List<@NotNull @Positive @Max(260) Double> manualBounds,
            @NotNull @Size(min = 5, max = 5) List<@NotNull @Size(max = 40) String> names,
            @NotNull @Size(min = 5, max = 5) List<@NotNull @PositiveOrZero @Max(100) Double> loadCoef) {

        @JsonIgnore
        @AssertTrue(message = "границы зон должны возрастать")
        public boolean isAscending() {
            return ascending(pct) && ascending(manualBounds);
        }
    }

    public record Z2Band(@Positive @Max(260) double lo, @Positive @Max(260) double hi) {

        @JsonIgnore
        @AssertTrue(message = "низ полки Z2 должен быть меньше верха")
        public boolean isOrdered() {
            return lo < hi;
        }
    }

    public record Analysis(
            @Positive @Max(260) double effortHrThreshold,
            @Positive @Max(3600) double effortMinSec,
            @PositiveOrZero @Max(100) double movingSpeedKmh,
            @Min(1) @Max(600) int altitudeSmoothing,
            @Min(1) @Max(600) int speedRolling,
            @Min(1) @Max(600) double flatMinMinutes,
            @JsonProperty("flat_net_climb_m_per_min") @Positive @Max(1000) double flatNetClimbMPerMin,
            @PositiveOrZero @Max(100) double flatMinSpeedKmh,
            @PositiveOrZero @Max(600) double skipFirstMinutes,
            @PositiveOrZero @Max(100) double decouplingGoodPct,
            @PositiveOrZero @Max(100) double heatAllowancePp,
            @Positive @Max(200) double hrArtifactSpeedKmh,
            @Min(10) @Max(20000) int seriesMaxPoints) {
    }

    public record Power(
            @NotNull @Size(min = 1, max = 6) List<@NotNull @Positive @DecimalMax("5") Double> zonesPct,
            @Positive @Max(36000) double ergBlockMinSec,
            @DecimalMin("0.1") @DecimalMax("3") double ergBlockMinPctFtp) {

        @JsonIgnore
        @AssertTrue(message = "зоны мощности должны возрастать")
        public boolean isAscending() {
            return ascending(zonesPct);
        }
    }

    public record Temperature(@Min(-60) @Max(60) double coldBelow, @Min(-60) @Max(60) double hotAbove) {
    }

    public record Time(boolean fitIsLocal, @Min(-14) @Max(14) double offsetHours) {
    }

    private static boolean ascending(List<Double> values) {
        if (values == null) {
            return true;
        }
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) == null || values.get(i - 1) == null || values.get(i) <= values.get(i - 1)) {
                return false;
            }
        }
        return true;
    }
}

package ru.lopon.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnalysisResult(
        Meta meta,
        Summary summary,
        HrZones hrZones,
        Double ef,
        Double efPower,
        Decoupling decoupling,
        List<Effort> efforts,
        HalvesAltitude halvesAltitude,
        Power power,
        List<String> warnings,
        @JsonInclude(JsonInclude.Include.NON_NULL) Series series,
        SettingsUsed settingsUsed) {

    public AnalysisResult withoutSeries() {
        return new AnalysisResult(meta, summary, hrZones, ef, efPower, decoupling, efforts, halvesAltitude, power, warnings,
                null, settingsUsed);
    }

    public record Meta(
            String filename,
            String startTime,
            String date,
            String sport,
            String subSport,
            boolean indoor,
            boolean hasGps,
            boolean hasPower,
            boolean hasHr,
            String device) {
    }

    public record Summary(
            double distanceKm,
            double elapsedMin,
            double movingMin,
            double stoppedMin,
            double avgSpeedKmh,
            double maxSpeedKmh,
            Long avgHr,
            Integer maxHr,
            Double maxHrSpeedKmh,
            Long avgCad,
            Integer maxCad,
            Long cadGe80Pct,
            Long cadGe90Pct,
            long ascentM,
            long descentM,
            Integer ascentSessionM,
            Temp temp,
            String tempContext,
            Integer kcal,
            long load) {
    }

    public record Temp(int min, double avg, int max) {
    }

    public record HrZones(List<Integer> bounds, List<Zone> zones, Z2BandShare z2Band, double leZ2TopPct) {
    }

    public record Zone(String name, int lo, Integer hi, double minutes, double pct) {
    }

    public record Z2BandShare(double lo, double hi, double minutes, double pct) {
    }

    public record Decoupling(
            String method,
            @JsonInclude(JsonInclude.Include.NON_NULL) Segment segment,
            @JsonInclude(JsonInclude.Include.NON_NULL) Half first,
            @JsonInclude(JsonInclude.Include.NON_NULL) Half second,
            @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
            Double pct,
            String verdict,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer longestFlatMin,
            boolean reliable) {

        static Decoupling none(String verdict) {
            return new Decoupling("none", null, null, null, null, null, verdict, null, false);
        }

        Decoupling withReliability(boolean value, String suffix) {
            return new Decoupling(method, segment, first, second, unit, pct, verdict + suffix, longestFlatMin, value);
        }
    }

    public record Segment(int startMin, int endMin, int minutes) {
    }

    public record Half(double value, long hr) {
    }

    public record Effort(double startMin, double minutes, long avgHr, int peakHr, Long avgPower) {
    }

    public record HalvesAltitude(AltitudeHalf first, AltitudeHalf second) {
    }

    public record AltitudeHalf(long ascent, long descent, long net) {
    }

    public record Power(
            double ftpUsed,
            Integer ftpInFit,
            long avg,
            long avgNonzero,
            int max,
            long np,
            @JsonProperty("if") double intensityFactor,
            long tss,
            double wkgFtp,
            List<Zone> zones,
            List<ErgBlock> ergBlocks) {
    }

    public record ErgBlock(
            double startMin,
            double minutes,
            long avgPower,
            Long pctFtp,
            double powerCvPct,
            Long avgCad,
            Long hrStart,
            Long hrEnd,
            Long hrAvg) {
    }

    public record Series(
            List<Double> tMin,
            List<Double> distKm,
            List<Double> alt,
            List<Integer> hr,
            List<Double> speed,
            List<Integer> cad,
            List<Integer> power,
            List<Integer> temp,
            List<Double> lat,
            List<Double> lon) {
    }

    public record SettingsUsed(List<Integer> hrBounds, double ftp, List<Double> z2Band) {
    }
}

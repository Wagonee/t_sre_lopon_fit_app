package ru.lopon.stats;

import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record Weeks(List<Week> weeks) {
    }

    public record Week(
            LocalDate weekStart, LocalDate weekEnd, int rides, double km, double movingH, long ascentM, long load,
            long z2BandPct, long leZ2TopPct, List<Double> z2Band, Longest longest, List<Day> days, List<String> temps) {
    }

    public record Longest(LocalDate date, double km, double movingMin, Integer avgHr) {
    }

    public record Day(LocalDate date, int rides, double movingMin, double km, double z2BandPct, boolean indoor) {
    }

    public record Trends(List<TrendPoint> rides, Wkg wkg) {
    }

    public record TrendPoint(
            Long id, LocalDate date, boolean indoor, Double ef, Double efPower, Double decouplingPct, String decouplingMethod,
            Integer avgHr, double distanceKm, double movingMin, int load, String tempContext, Integer np, double z2BandPct) {
    }

    public record Wkg(double ftp, double weightKg, double wkg, double targetWkg, long targetFtp, long gapW) {
    }
}

package ru.lopon.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lopon.analysis.AnalysisSettings;
import ru.lopon.analysis.Py;
import ru.lopon.settings.SettingsService;
import ru.lopon.stats.StatsDtos.Day;
import ru.lopon.stats.StatsDtos.Longest;
import ru.lopon.stats.StatsDtos.TrendPoint;
import ru.lopon.stats.StatsDtos.Trends;
import ru.lopon.stats.StatsDtos.Week;
import ru.lopon.stats.StatsDtos.Weeks;
import ru.lopon.stats.StatsDtos.Wkg;
import ru.lopon.workout.WorkoutSummary;
import ru.lopon.workout.WorkoutSummaryRepository;

@Service
public class StatsService {

    private final WorkoutSummaryRepository summaries;
    private final SettingsService settings;

    public StatsService(WorkoutSummaryRepository summaries, SettingsService settings) {
        this.summaries = summaries;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Weeks weeks(long userId) {
        AnalysisSettings.Z2Band z2 = settings.current(userId).z2Band();
        Map<LocalDate, List<WorkoutSummary>> byWeek = new LinkedHashMap<>();
        for (WorkoutSummary s : summaries.findAllByUserIdChronologically(userId)) {
            byWeek.computeIfAbsent(date(s).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), k -> new ArrayList<>()).add(s);
        }
        List<Week> weeks = new ArrayList<>();
        byWeek.keySet().stream().sorted(Comparator.reverseOrder()).forEach(start -> {
            List<WorkoutSummary> rs = byWeek.get(start);
            double hrMinSum = sum(rs, WorkoutSummary::getHrZoneMinutes);
            double hrMin = hrMinSum == 0 ? 1 : hrMinSum;
            double z2bMin = sum(rs, WorkoutSummary::getZ2BandMin);
            double leMin = sum(rs, s -> s.getLeZ2TopPct() / 100 * s.getHrZoneMinutes());
            List<Day> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate d = start.plusDays(i);
                List<WorkoutSummary> dr = rs.stream().filter(s -> date(s).equals(d)).toList();
                double moving = sum(dr, WorkoutSummary::getMovingMin);
                days.add(new Day(d, dr.size(), Py.round(moving, 1), Py.round(sum(dr, WorkoutSummary::getDistanceKm), 1),
                        Py.round(sum(dr, s -> s.getZ2BandPct() * s.getMovingMin()) / (moving == 0 ? 1 : moving), 0),
                        dr.stream().anyMatch(s -> s.getWorkout().isIndoor())));
            }
            WorkoutSummary longest = rs.getFirst();
            for (WorkoutSummary s : rs) {
                if (s.getMovingMin() > longest.getMovingMin()) {
                    longest = s;
                }
            }
            weeks.add(new Week(start, start.plusDays(6), rs.size(), Py.round(sum(rs, WorkoutSummary::getDistanceKm), 1),
                    Py.round(sum(rs, WorkoutSummary::getMovingMin) / 60, 1),
                    Py.round(rs.stream().mapToLong(WorkoutSummary::getAscentM).sum()),
                    Py.round(rs.stream().mapToLong(WorkoutSummary::getLoad).sum()),
                    Py.round(100 * z2bMin / hrMin), Py.round(100 * leMin / hrMin), List.of(z2.lo(), z2.hi()),
                    new Longest(date(longest), longest.getDistanceKm(), longest.getMovingMin(), longest.getAvgHr()),
                    days, rs.stream().map(WorkoutSummary::getTempContext).toList()));
        });
        return new Weeks(weeks);
    }

    @Transactional(readOnly = true)
    public Trends trends(long userId) {
        AnalysisSettings.Athlete athlete = settings.current(userId).athlete();
        List<TrendPoint> points = summaries.findAllByUserIdChronologically(userId).stream()
                .map(s -> new TrendPoint(s.getWorkoutId(), date(s), s.getWorkout().isIndoor(), s.getEf(), s.getEfPower(),
                        s.isDecouplingReliable() ? s.getDecouplingPct() : null, s.getDecouplingMethod(), s.getAvgHr(),
                        s.getDistanceKm(), s.getMovingMin(), s.getLoad(), s.getTempContext(), s.getNp(), s.getZ2BandPct()))
                .toList();
        double ftp = athlete.ftp();
        double w = athlete.weightKg();
        return new Trends(points, new Wkg(ftp, w, Py.round(ftp / w, 2), athlete.targetWkg(),
                Py.round(athlete.targetWkg() * w), Py.round(athlete.targetWkg() * w - ftp)));
    }

    private static LocalDate date(WorkoutSummary s) {
        return s.getWorkout().getWorkoutDate();
    }

    private static double sum(List<WorkoutSummary> rows, ToDoubleFunction<WorkoutSummary> value) {
        return Py.sum(rows.stream().mapToDouble(value).toArray());
    }
}

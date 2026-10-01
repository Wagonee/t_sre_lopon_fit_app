package ru.lopon.workout;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import tools.jackson.databind.JsonNode;

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record WorkoutPatch(@Size(min = 1, max = 200) String title, @Size(max = 10000) String description) {
    }

    public record Metrics(
            double distanceKm, double elapsedMin, double movingMin, double stoppedMin, double avgSpeedKmh, double maxSpeedKmh,
            Integer avgHr, Integer maxHr, Double maxHrSpeedKmh, Integer avgCad, Integer maxCad, Integer cadGe80Pct,
            Integer cadGe90Pct, int ascentM, int descentM, Integer ascentDeviceM, Integer tempMin, Double tempAvg,
            Integer tempMax, String tempContext, Integer kcal, int load, Double ef, Double efPower, Double decouplingPct,
            String decouplingMethod, boolean decouplingReliable, double z2BandPct, double leZ2TopPct, Integer avgPower,
            Integer np, Double intensityFactor, Integer tss, double ftpUsed, int warningCount) {

        static Metrics of(WorkoutSummary s) {
            return new Metrics(s.getDistanceKm(), s.getElapsedMin(), s.getMovingMin(), s.getStoppedMin(), s.getAvgSpeedKmh(),
                    s.getMaxSpeedKmh(), s.getAvgHr(), s.getMaxHr(), s.getMaxHrSpeedKmh(), s.getAvgCad(), s.getMaxCad(),
                    s.getCadGe80Pct(), s.getCadGe90Pct(), s.getAscentM(), s.getDescentM(), s.getAscentDeviceM(), s.getTempMin(),
                    s.getTempAvg(), s.getTempMax(), s.getTempContext(), s.getKcal(), s.getLoad(), s.getEf(), s.getEfPower(),
                    s.getDecouplingPct(), s.getDecouplingMethod(), s.isDecouplingReliable(), s.getZ2BandPct(), s.getLeZ2TopPct(),
                    s.getAvgPower(), s.getNp(), s.getIntensityFactor(), s.getTss(), s.getFtpUsed(), s.getWarningCount());
        }
    }

    public record WorkoutItem(
            Long id, String title, String filename, LocalDateTime startTime, LocalDate date, boolean indoor, boolean hasPower,
            Metrics metrics) {

        static WorkoutItem of(WorkoutSummary s) {
            Workout w = s.getWorkout();
            return new WorkoutItem(w.getId(), w.getTitle(), w.getFilename(), w.getStartTime(), w.getWorkoutDate(), w.isIndoor(),
                    w.isHasPower(), Metrics.of(s));
        }
    }

    public record WorkoutDetails(
            Long id, String title, String description, String filename, String sha1, LocalDateTime startTime, LocalDate date,
            String sport, String subSport, boolean indoor, boolean hasGps, boolean hasPower, boolean hasHr, String device,
            Instant createdAt, Instant updatedAt, Metrics metrics, JsonNode analysis) {

        static WorkoutDetails of(WorkoutSummary s, JsonNode analysis) {
            Workout w = s.getWorkout();
            return new WorkoutDetails(w.getId(), w.getTitle(), w.getDescription(), w.getFilename(), w.getSha1(), w.getStartTime(),
                    w.getWorkoutDate(), w.getSport(), w.getSubSport(), w.isIndoor(), w.isHasGps(), w.isHasPower(), w.isHasHr(),
                    w.getDevice(), w.getCreatedAt(), w.getUpdatedAt(), Metrics.of(s), analysis);
        }
    }

    public record SummaryResponse(Long workoutId, Metrics metrics, JsonNode analysis, Instant analyzedAt) {
    }

    public record TrackPointResponse(
            int seq, double tMin, double distanceKm, Double latitude, Double longitude, double altitudeM, Integer heartRate,
            Integer cadence, Integer power, Double speedKmh, Integer temperature) {

        static TrackPointResponse of(TrackPoint p) {
            return new TrackPointResponse(p.getSeq(), p.getTMin(), p.getDistanceKm(), p.getLatitude(), p.getLongitude(),
                    p.getAltitudeM(), p.getHeartRate(), p.getCadence(), p.getPower(), p.getSpeedKmh(), p.getTemperature());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record ReanalyzeResult(int reanalyzed, List<String> errors) {
    }

    public record StoredFile(String filename, byte[] content) {
    }
}

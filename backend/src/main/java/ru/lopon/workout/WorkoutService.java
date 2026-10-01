package ru.lopon.workout;

import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lopon.analysis.AnalysisResult;
import ru.lopon.analysis.AnalysisSettings;
import ru.lopon.fit.FitParseException;
import ru.lopon.settings.SettingsService;
import ru.lopon.web.ApiException;
import ru.lopon.workout.WorkoutDtos.PageResponse;
import ru.lopon.workout.WorkoutDtos.ReanalyzeResult;
import ru.lopon.workout.WorkoutDtos.StoredFile;
import ru.lopon.workout.WorkoutDtos.SummaryResponse;
import ru.lopon.workout.WorkoutDtos.TrackPointResponse;
import ru.lopon.workout.WorkoutDtos.WorkoutDetails;
import ru.lopon.workout.WorkoutDtos.WorkoutItem;
import ru.lopon.workout.WorkoutDtos.WorkoutPatch;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WorkoutService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutService.class);
    private static final String WORKOUT = "Заезд";

    private final WorkoutRepository workouts;
    private final WorkoutSummaryRepository summaries;
    private final WorkoutFileRepository files;
    private final TrackPointRepository trackPoints;
    private final WorkoutStore store;
    private final AnalysisRunner analyzer;
    private final SettingsService settings;
    private final JsonMapper mapper;
    private final MeterRegistry meters;

    public WorkoutService(WorkoutRepository workouts, WorkoutSummaryRepository summaries, WorkoutFileRepository files,
                          TrackPointRepository trackPoints, WorkoutStore store, AnalysisRunner analyzer,
                          SettingsService settings, JsonMapper mapper, MeterRegistry meters) {
        this.workouts = workouts;
        this.summaries = summaries;
        this.files = files;
        this.trackPoints = trackPoints;
        this.store = store;
        this.analyzer = analyzer;
        this.settings = settings;
        this.mapper = mapper;
        this.meters = meters;
    }

    public long upload(long userId, String filename, byte[] content) {
        String sha1 = sha1(content);
        workouts.findIdByUserIdAndSha1(userId, sha1).ifPresent(id -> {
            throw duplicate(id);
        });
        AnalysisResult result;
        try {
            result = analyzer.analyze(content, settings.current(userId), filename);
        } catch (FitParseException e) {
            count("invalid");
            throw e;
        }
        try {
            long id = store.create(userId, sha1, filename, content, result);
            count("created");
            log.atInfo().addKeyValue("workout_id", id).addKeyValue("points", result.series().tMin().size()).log("workout uploaded");
            return id;
        } catch (DataIntegrityViolationException e) {
            throw duplicate(workouts.findIdByUserIdAndSha1(userId, sha1).orElseThrow(() -> e));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkoutItem> list(long userId, int page, int size) {
        return PageResponse.of(summaries.findPageByUserId(userId, PageRequest.of(page, size)).map(WorkoutItem::of));
    }

    @Transactional(readOnly = true)
    public WorkoutDetails details(long userId, long id) {
        WorkoutSummary summary = summary(userId, id);
        return WorkoutDetails.of(summary, mapper.readTree(summary.getAnalysis()));
    }

    @Transactional
    public WorkoutDetails patch(long userId, long id, WorkoutPatch patch) {
        WorkoutSummary summary = summary(userId, id);
        summary.getWorkout().rename(patch.title(), patch.description());
        return WorkoutDetails.of(summary, mapper.readTree(summary.getAnalysis()));
    }

    @Transactional
    public void delete(long userId, long id) {
        workouts.delete(workout(userId, id));
        log.atInfo().addKeyValue("workout_id", id).log("workout deleted");
    }

    @Transactional(readOnly = true)
    public SummaryResponse summaryOf(long userId, long id) {
        WorkoutSummary s = summary(userId, id);
        return new SummaryResponse(s.getWorkoutId(), WorkoutDtos.Metrics.of(s), mapper.readTree(s.getAnalysis()), s.getAnalyzedAt());
    }

    @Transactional(readOnly = true)
    public List<TrackPointResponse> trackPoints(long userId, long id) {
        workout(userId, id);
        return trackPoints.findByWorkoutIdOrderBySeq(id).stream().map(TrackPointResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public StoredFile file(long userId, long id) {
        Workout workout = workout(userId, id);
        return new StoredFile(workout.getFilename(), files.findById(id).orElseThrow(() -> ApiException.notFound("Файл")).getContent());
    }

    public ReanalyzeResult reanalyze(long userId) {
        return reanalyze(workouts.findIdsByUserId(userId), Map.of(userId, settings.current(userId)));
    }

    public ReanalyzeResult reanalyzeAll() {
        return reanalyze(workouts.findAllIds(), Map.of());
    }

    private ReanalyzeResult reanalyze(List<Long> ids, Map<Long, AnalysisSettings> settingsByUser) {
        Map<Long, AnalysisSettings> cache = new HashMap<>(settingsByUser);
        List<String> errors = new ArrayList<>();
        int done = 0;
        for (Long id : ids) {
            Workout workout = workouts.findById(id).orElse(null);
            if (workout == null) {
                continue;
            }
            try {
                byte[] content = files.findById(id).orElseThrow(() -> new IllegalStateException("исходный FIT не найден")).getContent();
                AnalysisSettings userSettings = cache.computeIfAbsent(workout.getUserId(), settings::current);
                store.replaceAnalysis(id, analyzer.analyze(content, userSettings, workout.getFilename()));
                done++;
            } catch (RuntimeException e) {
                errors.add(workout.getFilename() + ": " + e.getMessage());
            }
        }
        log.atInfo().addKeyValue("reanalyzed", done).addKeyValue("errors", errors.size()).log("workouts reanalyzed");
        return new ReanalyzeResult(done, errors);
    }

    private WorkoutSummary summary(long userId, long id) {
        return summaries.findByWorkoutIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound(WORKOUT));
    }

    private Workout workout(long userId, long id) {
        return workouts.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound(WORKOUT));
    }

    private ApiException duplicate(long existingId) {
        count("duplicate");
        return new ApiException(HttpStatus.CONFLICT, "Этот файл уже загружен", Map.of("existing_id", existingId));
    }

    private void count(String outcome) {
        meters.counter("lopon.workouts.uploaded", "outcome", outcome).increment();
    }

    private static String sha1(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

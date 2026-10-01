package ru.lopon.workout;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.lopon.analysis.AnalysisResult;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WorkoutStore {

    private final WorkoutRepository workouts;
    private final WorkoutFileRepository files;
    private final WorkoutSummaryRepository summaries;
    private final TrackPointWriter trackPoints;
    private final JsonMapper mapper;

    public WorkoutStore(WorkoutRepository workouts, WorkoutFileRepository files, WorkoutSummaryRepository summaries,
                        TrackPointWriter trackPoints, JsonMapper mapper) {
        this.workouts = workouts;
        this.files = files;
        this.summaries = summaries;
        this.trackPoints = trackPoints;
        this.mapper = mapper;
    }

    @Transactional
    public long create(long userId, String sha1, String filename, byte[] content, AnalysisResult result) {
        Workout workout = workouts.saveAndFlush(new Workout(userId, sha1, filename, result.meta()));
        files.save(new WorkoutFile(workout, content));
        summaries.save(new WorkoutSummary(workout, result, json(result)));
        trackPoints.replace(workout.getId(), result.series());
        return workout.getId();
    }

    @Transactional
    public void replaceAnalysis(long workoutId, AnalysisResult result) {
        WorkoutSummary summary = summaries.findById(workoutId).orElseThrow();
        summary.getWorkout().applyMeta(result.meta());
        summary.apply(result, json(result));
        trackPoints.replace(workoutId, result.series());
    }

    private String json(AnalysisResult result) {
        return mapper.writeValueAsString(result.withoutSeries());
    }
}

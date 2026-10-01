package ru.lopon.workout;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackPointRepository extends JpaRepository<TrackPoint, TrackPoint.Key> {

    List<TrackPoint> findByWorkoutIdOrderBySeq(Long workoutId);
}

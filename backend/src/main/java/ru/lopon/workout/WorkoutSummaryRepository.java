package ru.lopon.workout;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkoutSummaryRepository extends JpaRepository<WorkoutSummary, Long> {

    @Query(value = "select s from WorkoutSummary s join fetch s.workout w where w.userId = :userId order by w.startTime desc, w.id desc",
            countQuery = "select count(s) from WorkoutSummary s where s.workout.userId = :userId")
    Page<WorkoutSummary> findPageByUserId(Long userId, Pageable pageable);

    @Query("select s from WorkoutSummary s join fetch s.workout w where w.userId = :userId order by w.startTime, w.id")
    List<WorkoutSummary> findAllByUserIdChronologically(Long userId);

    @Query("select s from WorkoutSummary s join fetch s.workout w where w.id = :id and w.userId = :userId")
    Optional<WorkoutSummary> findByWorkoutIdAndUserId(Long id, Long userId);
}

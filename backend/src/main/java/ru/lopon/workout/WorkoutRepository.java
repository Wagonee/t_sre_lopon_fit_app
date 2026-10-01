package ru.lopon.workout;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    Optional<Workout> findByIdAndUserId(Long id, Long userId);

    @Query("select w.id from Workout w where w.userId = :userId and w.sha1 = :sha1")
    Optional<Long> findIdByUserIdAndSha1(Long userId, String sha1);

    @Query("select w.id from Workout w where w.userId = :userId order by w.startTime, w.id")
    List<Long> findIdsByUserId(Long userId);

    @Query("select w.id from Workout w order by w.id")
    List<Long> findAllIds();
}

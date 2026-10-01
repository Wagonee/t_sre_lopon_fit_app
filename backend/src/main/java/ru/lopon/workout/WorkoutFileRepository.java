package ru.lopon.workout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutFileRepository extends JpaRepository<WorkoutFile, Long> {
}

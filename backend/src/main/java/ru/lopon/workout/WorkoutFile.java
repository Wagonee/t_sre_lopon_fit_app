package ru.lopon.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workout_file")
public class WorkoutFile {

    @Id
    @Column(name = "workout_id")
    private Long workoutId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id")
    private Workout workout;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    protected WorkoutFile() {
    }

    public WorkoutFile(Workout workout, byte[] content) {
        this.workout = workout;
        this.content = content;
        this.sizeBytes = content.length;
    }

    public byte[] getContent() {
        return content;
    }
}

package ru.lopon.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.hibernate.annotations.DynamicUpdate;
import ru.lopon.analysis.AnalysisResult;

@Entity
@Table(name = "workout")
@DynamicUpdate
public class Workout {

    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String sha1;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(nullable = false)
    private String sport;

    @Column(name = "sub_sport")
    private String subSport;

    @Column(nullable = false)
    private boolean indoor;

    @Column(name = "has_gps", nullable = false)
    private boolean hasGps;

    @Column(name = "has_power", nullable = false)
    private boolean hasPower;

    @Column(name = "has_hr", nullable = false)
    private boolean hasHr;

    @Column(nullable = false)
    private String device;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workout() {
    }

    public Workout(long userId, String sha1, String filename, AnalysisResult.Meta meta) {
        this.userId = userId;
        this.sha1 = sha1;
        this.filename = filename;
        this.createdAt = Instant.now();
        applyMeta(meta);
        this.title = (indoor ? "Станок " : "Заезд ") + startTime.format(TITLE_TIME);
    }

    public void applyMeta(AnalysisResult.Meta meta) {
        startTime = LocalDateTime.parse(meta.startTime());
        workoutDate = LocalDate.parse(meta.date());
        sport = meta.sport();
        subSport = meta.subSport();
        indoor = meta.indoor();
        hasGps = meta.hasGps();
        hasPower = meta.hasPower();
        hasHr = meta.hasHr();
        device = meta.device();
        updatedAt = Instant.now();
    }

    public void rename(String newTitle, String newDescription) {
        if (newTitle != null) {
            title = newTitle.trim();
        }
        if (newDescription != null) {
            description = newDescription.isBlank() ? null : newDescription;
        }
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSha1() {
        return sha1;
    }

    public String getFilename() {
        return filename;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }

    public String getSport() {
        return sport;
    }

    public String getSubSport() {
        return subSport;
    }

    public boolean isIndoor() {
        return indoor;
    }

    public boolean isHasGps() {
        return hasGps;
    }

    public boolean isHasPower() {
        return hasPower;
    }

    public boolean isHasHr() {
        return hasHr;
    }

    public String getDevice() {
        return device;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

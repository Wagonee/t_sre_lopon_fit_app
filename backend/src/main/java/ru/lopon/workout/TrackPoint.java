package ru.lopon.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "track_point")
@IdClass(TrackPoint.Key.class)
public class TrackPoint {

    @Id
    @Column(name = "workout_id")
    private Long workoutId;

    @Id
    private Integer seq;

    @Column(name = "t_min", nullable = false)
    private double tMin;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    private Double latitude;

    private Double longitude;

    @Column(name = "altitude_m", nullable = false)
    private double altitudeM;

    @Column(name = "heart_rate")
    private Integer heartRate;

    private Integer cadence;

    private Integer power;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    private Integer temperature;

    protected TrackPoint() {
    }

    public record Key(Long workoutId, Integer seq) implements Serializable {
    }

    public Integer getSeq() {
        return seq;
    }

    public double getTMin() {
        return tMin;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public double getAltitudeM() {
        return altitudeM;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public Integer getCadence() {
        return cadence;
    }

    public Integer getPower() {
        return power;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public Integer getTemperature() {
        return temperature;
    }
}

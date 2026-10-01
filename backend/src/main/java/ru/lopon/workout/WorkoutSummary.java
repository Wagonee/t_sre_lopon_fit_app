package ru.lopon.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.lopon.analysis.AnalysisResult;
import ru.lopon.analysis.Py;

@Entity
@Table(name = "workout_summary")
public class WorkoutSummary {

    @Id
    @Column(name = "workout_id")
    private Long workoutId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id")
    private Workout workout;

    @Column(name = "distance_km") private double distanceKm;
    @Column(name = "elapsed_min") private double elapsedMin;
    @Column(name = "moving_min") private double movingMin;
    @Column(name = "stopped_min") private double stoppedMin;
    @Column(name = "avg_speed_kmh") private double avgSpeedKmh;
    @Column(name = "max_speed_kmh") private double maxSpeedKmh;
    @Column(name = "avg_hr") private Integer avgHr;
    @Column(name = "max_hr") private Integer maxHr;
    @Column(name = "max_hr_speed_kmh") private Double maxHrSpeedKmh;
    @Column(name = "avg_cad") private Integer avgCad;
    @Column(name = "max_cad") private Integer maxCad;
    @Column(name = "cad_ge80_pct") private Integer cadGe80Pct;
    @Column(name = "cad_ge90_pct") private Integer cadGe90Pct;
    @Column(name = "ascent_m") private int ascentM;
    @Column(name = "descent_m") private int descentM;
    @Column(name = "ascent_device_m") private Integer ascentDeviceM;
    @Column(name = "temp_min") private Integer tempMin;
    @Column(name = "temp_avg") private Double tempAvg;
    @Column(name = "temp_max") private Integer tempMax;
    @Column(name = "temp_context") private String tempContext;
    private Integer kcal;
    private int load;
    private Double ef;
    @Column(name = "ef_power") private Double efPower;
    @Column(name = "decoupling_pct") private Double decouplingPct;
    @Column(name = "decoupling_method") private String decouplingMethod;
    @Column(name = "decoupling_reliable") private boolean decouplingReliable;
    @Column(name = "hr_zone_minutes") private double hrZoneMinutes;
    @Column(name = "z2_band_min") private double z2BandMin;
    @Column(name = "z2_band_pct") private double z2BandPct;
    @Column(name = "le_z2_top_pct") private double leZ2TopPct;
    @Column(name = "avg_power") private Integer avgPower;
    private Integer np;
    @Column(name = "intensity_factor") private Double intensityFactor;
    private Integer tss;
    @Column(name = "ftp_used") private double ftpUsed;
    @Column(name = "warning_count") private int warningCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String analysis;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected WorkoutSummary() {
    }

    public WorkoutSummary(Workout workout, AnalysisResult result, String analysisJson) {
        this.workout = workout;
        apply(result, analysisJson);
    }

    public void apply(AnalysisResult r, String analysisJson) {
        AnalysisResult.Summary s = r.summary();
        distanceKm = s.distanceKm();
        elapsedMin = s.elapsedMin();
        movingMin = s.movingMin();
        stoppedMin = s.stoppedMin();
        avgSpeedKmh = s.avgSpeedKmh();
        maxSpeedKmh = s.maxSpeedKmh();
        avgHr = toInt(s.avgHr());
        maxHr = s.maxHr();
        maxHrSpeedKmh = s.maxHrSpeedKmh();
        avgCad = toInt(s.avgCad());
        maxCad = s.maxCad();
        cadGe80Pct = toInt(s.cadGe80Pct());
        cadGe90Pct = toInt(s.cadGe90Pct());
        ascentM = (int) s.ascentM();
        descentM = (int) s.descentM();
        ascentDeviceM = s.ascentSessionM();
        tempMin = s.temp() == null ? null : s.temp().min();
        tempAvg = s.temp() == null ? null : s.temp().avg();
        tempMax = s.temp() == null ? null : s.temp().max();
        tempContext = s.tempContext();
        kcal = s.kcal();
        load = (int) s.load();
        ef = r.ef();
        efPower = r.efPower();
        decouplingPct = r.decoupling().pct();
        decouplingMethod = r.decoupling().method();
        decouplingReliable = r.decoupling().reliable();
        hrZoneMinutes = Py.sum(r.hrZones().zones().stream().map(AnalysisResult.Zone::minutes).toList());
        z2BandMin = r.hrZones().z2Band().minutes();
        z2BandPct = r.hrZones().z2Band().pct();
        leZ2TopPct = r.hrZones().leZ2TopPct();
        avgPower = r.power() == null ? null : (int) r.power().avg();
        np = r.power() == null ? null : (int) r.power().np();
        intensityFactor = r.power() == null ? null : r.power().intensityFactor();
        tss = r.power() == null ? null : (int) r.power().tss();
        ftpUsed = r.settingsUsed().ftp();
        warningCount = r.warnings().size();
        analysis = analysisJson;
        analyzedAt = Instant.now();
    }

    private static Integer toInt(Long value) {
        return value == null ? null : value.intValue();
    }

    public Workout getWorkout() { return workout; }
    public Long getWorkoutId() { return workoutId; }
    public double getDistanceKm() { return distanceKm; }
    public double getElapsedMin() { return elapsedMin; }
    public double getMovingMin() { return movingMin; }
    public double getStoppedMin() { return stoppedMin; }
    public double getAvgSpeedKmh() { return avgSpeedKmh; }
    public double getMaxSpeedKmh() { return maxSpeedKmh; }
    public Integer getAvgHr() { return avgHr; }
    public Integer getMaxHr() { return maxHr; }
    public Double getMaxHrSpeedKmh() { return maxHrSpeedKmh; }
    public Integer getAvgCad() { return avgCad; }
    public Integer getMaxCad() { return maxCad; }
    public Integer getCadGe80Pct() { return cadGe80Pct; }
    public Integer getCadGe90Pct() { return cadGe90Pct; }
    public int getAscentM() { return ascentM; }
    public int getDescentM() { return descentM; }
    public Integer getAscentDeviceM() { return ascentDeviceM; }
    public Integer getTempMin() { return tempMin; }
    public Double getTempAvg() { return tempAvg; }
    public Integer getTempMax() { return tempMax; }
    public String getTempContext() { return tempContext; }
    public Integer getKcal() { return kcal; }
    public int getLoad() { return load; }
    public Double getEf() { return ef; }
    public Double getEfPower() { return efPower; }
    public Double getDecouplingPct() { return decouplingPct; }
    public String getDecouplingMethod() { return decouplingMethod; }
    public boolean isDecouplingReliable() { return decouplingReliable; }
    public double getHrZoneMinutes() { return hrZoneMinutes; }
    public double getZ2BandMin() { return z2BandMin; }
    public double getZ2BandPct() { return z2BandPct; }
    public double getLeZ2TopPct() { return leZ2TopPct; }
    public Integer getAvgPower() { return avgPower; }
    public Integer getNp() { return np; }
    public Double getIntensityFactor() { return intensityFactor; }
    public Integer getTss() { return tss; }
    public double getFtpUsed() { return ftpUsed; }
    public int getWarningCount() { return warningCount; }
    public String getAnalysis() { return analysis; }
    public Instant getAnalyzedAt() { return analyzedAt; }
}

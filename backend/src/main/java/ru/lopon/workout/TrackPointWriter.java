package ru.lopon.workout;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.lopon.analysis.AnalysisResult.Series;

@Component
public class TrackPointWriter {

    private static final String INSERT = """
            insert into track_point (workout_id, seq, t_min, distance_km, latitude, longitude, altitude_m,
                                     heart_rate, cadence, power, speed_kmh, temperature)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbc;

    public TrackPointWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void replace(long workoutId, Series s) {
        jdbc.update("delete from track_point where workout_id = ?", workoutId);
        List<Object[]> rows = new ArrayList<>(s.tMin().size());
        for (int i = 0; i < s.tMin().size(); i++) {
            rows.add(new Object[] {workoutId, i, s.tMin().get(i), s.distKm().get(i), s.lat().get(i), s.lon().get(i),
                    s.alt().get(i), s.hr().get(i), s.cad().get(i), s.power() == null ? null : s.power().get(i),
                    s.speed().get(i), s.temp() == null ? null : s.temp().get(i)});
        }
        jdbc.batchUpdate(INSERT, rows);
    }
}

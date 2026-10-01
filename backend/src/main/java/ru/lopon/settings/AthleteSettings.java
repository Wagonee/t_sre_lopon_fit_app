package ru.lopon.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "athlete_settings")
public class AthleteSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String settings;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AthleteSettings() {
    }

    public AthleteSettings(Long userId, String settings) {
        this.userId = userId;
        update(settings);
    }

    public void update(String json) {
        settings = json;
        updatedAt = Instant.now();
    }

    public String getSettings() {
        return settings;
    }
}

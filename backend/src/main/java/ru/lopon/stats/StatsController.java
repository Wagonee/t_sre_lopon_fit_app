package ru.lopon.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.lopon.auth.CurrentUser;
import ru.lopon.config.OpenApiConfig;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Статистика", description = "Недельные сводки и тренды")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @Operation(summary = "Недели Пн–Вс: объём, нагрузка, полка Z2, дни")
    @GetMapping("/weeks")
    public StatsDtos.Weeks weeks(CurrentUser user) {
        return stats.weeks(user.id());
    }

    @Operation(summary = "Тренды по заездам и прогресс к цели в Вт/кг")
    @GetMapping("/trends")
    public StatsDtos.Trends trends(CurrentUser user) {
        return stats.trends(user.id());
    }
}

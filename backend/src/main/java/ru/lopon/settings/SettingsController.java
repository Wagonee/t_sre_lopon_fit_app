package ru.lopon.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.lopon.analysis.AnalysisSettings;
import ru.lopon.auth.CurrentUser;
import ru.lopon.config.OpenApiConfig;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Настройки", description = "Пороги разбора: зоны, FTP, ровный сегмент, ERG-блоки")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @Operation(summary = "Текущие настройки и значения по умолчанию")
    @GetMapping
    public SettingsResponse get(CurrentUser user) {
        return new SettingsResponse(settings.current(user.id()), AnalysisSettings.DEFAULTS);
    }

    @Operation(summary = "Сохранить: тело сливается со значениями по умолчанию и проверяется")
    @PutMapping
    public SettingsResponse put(CurrentUser user, @RequestBody JsonNode body) {
        return new SettingsResponse(settings.save(user.id(), body), AnalysisSettings.DEFAULTS);
    }

    public record SettingsResponse(AnalysisSettings settings, AnalysisSettings defaults) {
    }
}

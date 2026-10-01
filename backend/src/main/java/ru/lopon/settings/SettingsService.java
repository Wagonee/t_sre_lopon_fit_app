package ru.lopon.settings;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lopon.analysis.AnalysisSettings;
import ru.lopon.analysis.SettingsMerger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SettingsService {

    private final AthleteSettingsRepository repository;
    private final JsonMapper mapper;
    private final Validator validator;

    public SettingsService(AthleteSettingsRepository repository, JsonMapper mapper, Validator validator) {
        this.repository = repository;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public AnalysisSettings current(long userId) {
        return repository.findById(userId)
                .map(s -> SettingsMerger.mergeWithDefaults(mapper, mapper.readTree(s.getSettings())))
                .orElse(AnalysisSettings.DEFAULTS);
    }

    @Transactional
    public AnalysisSettings save(long userId, JsonNode body) {
        AnalysisSettings merged = SettingsMerger.mergeWithDefaults(mapper, body);
        Set<ConstraintViolation<AnalysisSettings>> violations = validator.validate(merged);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        String json = mapper.writeValueAsString(merged);
        repository.findById(userId).ifPresentOrElse(s -> s.update(json), () -> repository.save(new AthleteSettings(userId, json)));
        return merged;
    }
}

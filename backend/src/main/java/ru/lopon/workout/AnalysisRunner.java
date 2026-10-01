package ru.lopon.workout;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import ru.lopon.analysis.AnalysisResult;
import ru.lopon.analysis.AnalysisSettings;
import ru.lopon.analysis.WorkoutAnalyzer;
import ru.lopon.config.LoponProperties;
import ru.lopon.fit.FitParser;

@Component
public class AnalysisRunner {

    private final Semaphore permits;
    private final Timer timer;

    public AnalysisRunner(LoponProperties properties, MeterRegistry registry) {
        this.permits = new Semaphore(properties.effectiveAnalysisConcurrency());
        this.timer = Timer.builder("lopon.fit.analysis").description("Разбор и анализ FIT-файла").register(registry);
    }

    public AnalysisResult analyze(byte[] fit, AnalysisSettings settings, String filename) {
        permits.acquireUninterruptibly();
        try {
            return timer.record(() -> WorkoutAnalyzer.analyze(FitParser.parse(fit), settings, filename));
        } finally {
            permits.release();
        }
    }
}

package ru.lopon.admin;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.lopon.auth.RefreshTokenRepository;
import ru.lopon.workout.WorkoutDtos.ReanalyzeResult;
import ru.lopon.workout.WorkoutService;

@Component
@ConditionalOnProperty("lopon.admin.command")
public class AdminCommandRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(AdminCommandRunner.class);

    private final String command;
    private final WorkoutService workouts;
    private final RefreshTokenRepository refreshTokens;
    private final TransactionTemplate tx;
    private int exitCode;

    public AdminCommandRunner(@Value("${lopon.admin.command}") String command, WorkoutService workouts,
                              RefreshTokenRepository refreshTokens, TransactionTemplate tx) {
        this.command = command;
        this.workouts = workouts;
        this.refreshTokens = refreshTokens;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (command) {
            case "reanalyze" -> {
                ReanalyzeResult result = workouts.reanalyzeAll();
                result.errors().forEach(e -> log.atWarn().addKeyValue("error", e).log("reanalyze failed"));
                exitCode = result.errors().isEmpty() ? 0 : 1;
            }
            case "purge-expired-tokens" -> {
                Integer deleted = tx.execute(status -> refreshTokens.deleteExpired(Instant.now()));
                log.atInfo().addKeyValue("deleted", deleted).log("expired refresh tokens purged");
            }
            default -> {
                log.error("unknown command {}", command);
                exitCode = 2;
            }
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}

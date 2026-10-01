package ru.lopon.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.lopon.LoponApplication;
import ru.lopon.support.IntegrationTest;

class AdminCommandsIT extends IntegrationTest {

    @Test
    void oneOffCommandsExitWithZero() {
        register();
        upload(register(), "synthetic_trainer");
        assertThat(LoponApplication.runAdmin("migrate", args(false))).isZero();
        assertThat(LoponApplication.runAdmin("reanalyze", args(true))).isZero();
        assertThat(LoponApplication.runAdmin("purge-expired-tokens", args(true))).isZero();
    }

    private static String[] args(boolean withSecret) {
        List<String> args = new ArrayList<>();
        databaseProperties().forEach((key, value) -> args.add("--" + key + "=" + value));
        if (withSecret) {
            args.add("--lopon.jwt.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtMTIzNA==");
        }
        args.add("--management.server.port=-1");
        args.add("--logging.level.root=WARN");
        return args.toArray(String[]::new);
    }
}

package ru.lopon;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import ru.lopon.admin.MigrationApplication;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LoponApplication {

    private static final Set<String> ADMIN_COMMANDS = Set.of("reanalyze", "purge-expired-tokens");

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        if ("migrate".equals(command) || ADMIN_COMMANDS.contains(command)) {
            System.exit(runAdmin(command, Arrays.copyOfRange(args, 1, args.length)));
        }
        SpringApplication.run(LoponApplication.class, args);
    }

    public static int runAdmin(String command, String... args) {
        boolean migrate = "migrate".equals(command);
        SpringApplicationBuilder builder = migrate
                ? new SpringApplicationBuilder(MigrationApplication.class)
                : new SpringApplicationBuilder(LoponApplication.class).properties("lopon.admin.command=" + command);
        String[] effective = migrate ? prepend("--spring.flyway.enabled=true", args) : args;
        try {
            return SpringApplication.exit(builder.web(WebApplicationType.NONE).run(effective));
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static String[] prepend(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }
}

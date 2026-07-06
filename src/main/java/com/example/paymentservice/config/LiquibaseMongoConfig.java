package com.example.paymentservice.config;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.exception.CommandExecutionException;
import liquibase.integration.spring.SpringResourceAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ResourceLoader;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.liquibase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiquibaseMongoConfig {

    @Value("${app.liquibase.uri}")
    private String mongoUri;

    @Value("${app.liquibase.change-log}")
    private String changeLogPath;

    private final ResourceLoader resourceLoader;

    @EventListener(ApplicationReadyEvent.class)
    public void runMigration() throws Exception {
        System.out.println("Starting Liquibase NoSQL Management Core...");

        SpringResourceAccessor resourceAccessor = new SpringResourceAccessor(resourceLoader);

        try {
            Scope.child(Scope.Attr.resourceAccessor, resourceAccessor, () -> {
                CommandScope updateCommand = new CommandScope("update");

                updateCommand.addArgumentValue("url", mongoUri);
                updateCommand.addArgumentValue("changelogFile", changeLogPath);

                updateCommand.execute();
            });
            System.out.println("Liquibase: Update command executed successfully. Database is up to date.");

        } catch (CommandExecutionException e) {
            System.err.println("Liquibase: Update command failed execution: " + e.getMessage());
            throw e;
        }
    }
}

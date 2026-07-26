package com.minispring.paymentservice.config;

import jakarta.annotation.PostConstruct;
import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.integration.spring.SpringResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.liquibase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiquibaseConfig {

    @Value("${app.liquibase.uri}")
    private String mongoUri;

    @Value("${app.liquibase.change-log}")
    private String changeLogPath;

    private final ApplicationContext applicationContext;

    @PostConstruct
    public void runMigration() {
        log.info("Starting Liquibase NoSQL Management Core...");

        try (SpringResourceAccessor resourceAccessor = new SpringResourceAccessor(applicationContext)) {

            Scope.child(Scope.Attr.resourceAccessor, resourceAccessor, () -> {
                CommandScope updateCommand = new CommandScope("update");

                updateCommand.addArgumentValue("url", mongoUri);
                updateCommand.addArgumentValue("changelogFile", changeLogPath);

                updateCommand.execute();
            });

            log.info("Liquibase: Update command executed successfully. MongoDB schema is up to date.");

        } catch (Exception e) {
            log.error("Liquibase: Fatal error during MongoDB schema update. Reason: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to execute Liquibase migrations for MongoDB", e);
        }
    }
}

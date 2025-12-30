package ai.intentchain.server.openapi.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Component
@ConfigurationProperties(prefix = "intentchain.server")
@Slf4j
public class ServerConfig implements InitializingBean {

    private String projectPath = ".";

    public Path getAbsoluteProjectPath() {
        return Paths.get(projectPath).toAbsolutePath();
    }

    @PostConstruct
    public void postConstruct() {
        log.info("=== ServerConfig PostConstruct ===");
        log.info("  - Project path: {}", projectPath);
        log.info("================================");
    }

    @Override
    public void afterPropertiesSet() {
        log.info("=== ServerConfig AfterPropertiesSet ===");
        log.info("  - Project path: {}", projectPath);
        log.info("=====================================");

        if (projectPath == null || projectPath.trim().isEmpty()) {
            log.warn("Project path is empty, using current directory");
            projectPath = ".";
        }
    }
}
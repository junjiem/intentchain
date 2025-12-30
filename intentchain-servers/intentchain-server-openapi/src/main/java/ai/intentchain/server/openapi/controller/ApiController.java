package ai.intentchain.server.openapi.controller;

import ai.intentchain.core.chain.data.CascadeResult;
import ai.intentchain.core.classifiers.data.TrainingData;
import ai.intentchain.sdk.data.project.Project;
import ai.intentchain.server.openapi.config.ServerConfig;
import ai.intentchain.server.openapi.service.ProjectService;
import ai.intentchain.server.openapi.utils.VersionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiController {

    private final ServerConfig serverConfig;
    private final ProjectService projectService;

    @Value("${server.address:0.0.0.0}")
    private String serverAddress;

    @Value("${server.port:8080}")
    private int serverPort;

    @Operation(summary = "Health checkup", description = "Health check endpoint")
    @ApiResponse(responseCode = "200", description = "Successful")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now(),
                "message", "IntentChain OpenAPI Server is running"
        ));
    }

    @Operation(summary = "System information",
            description = "Obtain system version and environment information")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful")})
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> system() {
        Runtime runtime = Runtime.getRuntime();
        return ResponseEntity.ok(Map.of(
                "system", Map.of(
                        "os", System.getProperty("os.name"),
                        "arch", System.getProperty("os.arch"),
                        "processors", runtime.availableProcessors(),
                        "maxMemory", runtime.maxMemory(),
                        "totalMemory", runtime.totalMemory(),
                        "freeMemory", runtime.freeMemory()
                ),
                "java", Map.of(
                        "version", System.getProperty("java.version"),
                        "vendor", System.getProperty("java.vendor"),
                        "runtime", System.getProperty("java.runtime.name")
                ),
                "server", Map.of(
                        "name", "IntentChain OpenAPI Server",
                        "version", VersionUtil.getVersion(),
                        "description", "IntentChain (Cascaded intent classifier) OpenAPI Server",
                        "address", serverAddress,
                        "port", serverPort
                ),
                "timestamp", LocalDateTime.now()
        ));
    }

    @Operation(summary = "Project information",
            description = "Obtain project information")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful")})
    @GetMapping("/project")
    public ResponseEntity<Map<String, Object>> project() {
        Project project = projectService.getProject();
        Map<String, Object> projectMap = Map.of(
                "path", serverConfig.getProjectPath(),
                "name", project.getName(),
                "description", project.getDescription() == null ? "<none>" : project.getDescription()
        );
        return ResponseEntity.ok(Map.of(
                "project", projectMap,
                "timestamp", LocalDateTime.now()
        ));
    }

    @Operation(summary = "Question classification", description = "Question classification")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Request parameter error"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/classify")
    public ResponseEntity<Map<String, Object>> classify(@Valid @RequestBody String question) {
        try {
            CascadeResult result = projectService.classify(question);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Classification successful",
                    "traceId", result.getTraceId(),
                    "intents", result.getIntents(),
                    "cascadePath", result.getCascadePath(),
                    "duration", result.getDuration().toMillis()
            ));
        } catch (Exception e) {
            log.error("Error processing question classification: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Classification training", description = "Classification training")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Request parameter error"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/train")
    public ResponseEntity<Map<String, String>> train(@Valid @RequestBody List<TrainingData> trainingData) {
        try {
            projectService.train(trainingData);
            return ResponseEntity.ok(Map.of("status", "success",
                    "message", "Training successful"));
        } catch (Exception e) {
            log.error("Error processing classification training: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}

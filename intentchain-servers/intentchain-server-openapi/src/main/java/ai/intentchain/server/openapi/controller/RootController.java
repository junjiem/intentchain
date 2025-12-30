package ai.intentchain.server.openapi.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @Hidden
    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to IntentChain OpenAPI Server",
                "swagger", "/swagger-ui/index.html",
                "docs", "/v3/api-docs",
                "health", "/api/v1/health"
        ));
    }

}

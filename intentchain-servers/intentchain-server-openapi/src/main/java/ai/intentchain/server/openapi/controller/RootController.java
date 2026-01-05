package ai.intentchain.server.openapi.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class RootController {

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    @Hidden
    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Welcome to IntentChain OpenAPI Server");
        response.put("health", "/api/v1/health");
        if (swaggerEnabled) {
            response.put("swagger", "/swagger-ui/index.html");
        }
        if (apiDocsEnabled) {
            response.put("docs", "/v3/api-docs");
        }
        return ResponseEntity.ok(response);
    }

}

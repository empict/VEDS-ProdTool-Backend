package veds.vedsprodtoolbackend.controller;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class TestController {

    @GetMapping("/test")
    public Map<String, Object> testConnection() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Backend is verbonden!");
        response.put("status", "success");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "VEDS-Prodtool-Backend");
        response.put("port", 8155);
        return response;
    }

    @PostMapping("/echo")
    public Map<String, Object> echoMessage(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bericht ontvangen!");
        response.put("receivedData", request);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}

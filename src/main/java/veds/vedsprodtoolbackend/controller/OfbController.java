package veds.vedsprodtoolbackend.controller;

import veds.vedsprodtoolbackend.service.OfbService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ofb")
public class OfbController {
    private final OfbService service;

    public OfbController(OfbService service) {
        this.service = service;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("ok", "ofb");
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) Boolean hasNav,
            @RequestParam(defaultValue = "ofbNumber") String sort,   // nieuw
            @RequestParam(defaultValue = "asc") String dir           // nieuw
    ) {
        return service.list(q, page, size, hasNav, sort, dir);
    }
}

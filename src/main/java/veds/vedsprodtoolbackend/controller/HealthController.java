package veds.vedsprodtoolbackend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/ping")
    public Map<String,Object> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/db")
    public Map<String,Object> dbPing() {
        // kleine read-only check: telt rijen en pakt een voorbeeldnummer
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.OFBParts", Integer.class);
        String sample = null;
        try {
            sample = jdbc.queryForObject("SELECT TOP 1 OFBNumber FROM dbo.OFBParts", String.class);
        } catch (Exception ignore) { /* leeg laten als de tabel leeg is */ }

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("db", "ok");
        m.put("ofbPartsCount", count);
        m.put("sampleOFBNumber", sample);
        return m;
    }
}

package veds.vedsprodtoolbackend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/db")
public class DbTestController {

    private final JdbcTemplate jdbc;

    public DbTestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/test")
    public String testDbConnection() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.OFBParts", Integer.class);
            return "Database OK! Aantal OFBParts records: " + count;
        } catch (Exception e) {
            return "❌ Database FOUT: " + e.getMessage();
        }
    }
}

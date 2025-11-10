package DbConnection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "DbConnection",                 // jouw main/service etc.
                "veds.vedsprodtoolbackend"      // controllers/repo/service
        }
)
public class TestSqlApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestSqlApplication.class, args);
    }
}
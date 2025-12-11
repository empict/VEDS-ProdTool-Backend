package DbConnection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "DbConnection") // ⬅️ Alleen SQL-diensten laden
public class TestSqlApplication {
    public static void main(String[] args) {

        // Start alleen de beans in DbConnection (dus SqlServerService)
        ConfigurableApplicationContext context =
                SpringApplication.run(TestSqlApplication.class, args);

        // Haal de service op uit de Spring-context
        SqlServerService sqlServerService = context.getBean(SqlServerService.class);

        // Voer de testquery uit
        sqlServerService.executeQuery();

        // Sluit de context na gebruik
        context.close();
    }
}

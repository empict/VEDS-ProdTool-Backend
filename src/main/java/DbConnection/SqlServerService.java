package DbConnection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class SqlServerService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void executeQuery() {
        try {
            System.out.println("Connecting to SQL Server...");
            System.out.println("Connected successfully!");

            // Execute query
            String query = "SELECT TOP 1000 [LinkedProjectID], [GenericID], [ProjectNumber], [OFBNumber], [LastChangedBy], [LastChangedDate] FROM [Koppel_DB_Test].[dbo].[LinkedProject]";

            System.out.println("\nExecuting query...");

            // Display results
            System.out.println("\nQuery Results:");
            System.out.println("=".repeat(120));
            System.out.printf("%-15s %-15s %-20s %-15s %-20s %-25s%n",
                    "LinkedProjectID", "GenericID", "ProjectNumber", "OFBNumber",
                    "LastChangedBy", "LastChangedDate");
            System.out.println("=".repeat(120));

            jdbcTemplate.query(query, (ResultSet resultSet, int rowNum) -> {
                System.out.printf("%-15s %-15s %-20s %-15s %-20s %-25s%n",
                        resultSet.getString("LinkedProjectID"),
                        resultSet.getString("GenericID"),
                        resultSet.getString("ProjectNumber"),
                        resultSet.getString("OFBNumber"),
                        resultSet.getString("LastChangedBy"),
                        resultSet.getTimestamp("LastChangedDate")
                );
                return null; // We're not returning anything, just printing
            });

            System.out.println("=".repeat(120));
            System.out.println("Query executed successfully!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
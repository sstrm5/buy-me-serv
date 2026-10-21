package infrastructure.db.postgres;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DatabaseConfig {
    public static void executeSchema(JdbcTemplate jdbcTemplate, Path schemaPath) {
        executeSchema(jdbcTemplate, new FileSystemResource(schemaPath));
    }

    public static void initializeSchema(JdbcTemplate jdbcTemplate) {
        executeSchema(jdbcTemplate, new ClassPathResource("db/postgres/schema.sql"));
    }

    private static void executeSchema(JdbcTemplate jdbcTemplate, Resource schema) {
        try {
            
            jdbcTemplate.execute(schema.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать SQL-схему " + schema.getDescription(), e);
        }
    }

    public static JdbcTemplate createJdbcTemplate(String webAppPath) {
        Map<String, String> env = readEnv(webAppPath);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();

        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(required(env, "POSTGRES_DB_URL"));
        dataSource.setUsername(required(env, "POSTGRES_DB_USERNAME"));
        dataSource.setPassword(required(env, "POSTGRES_DB_PASSWORD"));

        return new JdbcTemplate(dataSource);
    }

    private static Map<String, String> readEnv(String webAppPath) {
        Path envFile = Path.of(".env");
        if (webAppPath != null) {
            for (Path directory = Path.of(webAppPath).toAbsolutePath(); directory != null;
                 directory = directory.getParent()) {
                if (Files.isRegularFile(directory.resolve("pom.xml"))) {
                    envFile = directory.resolve(".env");
                    break;
                }
            }
        }

        Map<String, String> env = new HashMap<>();
        if (Files.notExists(envFile)) {
            return env;
        }

        try {
            for (String line : Files.readAllLines(envFile)) {
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }

                int separator = entry.indexOf('=');
                if (separator < 1) {
                    throw new IllegalStateException("Ожидается строка в формате KEY=VALUE в " + envFile);
                }
                String value = entry.substring(separator + 1).strip();
                if (value.length() >= 2 && ((value.startsWith("'") && value.endsWith("'"))
                        || (value.startsWith("\"") && value.endsWith("\"")))) {
                    value = value.substring(1, value.length() - 1);
                }
                env.put(entry.substring(0, separator).strip(), value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + envFile, e);
        }
        return env;
    }

    private static String required(Map<String, String> env, String key) {
        String value = System.getenv(key);
        if (value == null) {
            value = env.get(key);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Задайте " + key + " в окружении или файле .env");
        }
        return value;
    }
}

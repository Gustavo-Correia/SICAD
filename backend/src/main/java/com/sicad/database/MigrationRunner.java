package com.sicad.database;

import io.github.cdimascio.dotenv.Dotenv;
import org.flywaydb.core.Flyway;

public class MigrationRunner {

    public static void rodarMigrations() {
        Dotenv dotenv = Dotenv.configure()
    .directory("../")
    .ignoreIfMissing()
    .load();

        String host     = env(dotenv, "DB_HOST", "localhost");
        String port     = env(dotenv, "DB_PORT", "5432");
        String database = env(dotenv, "POSTGRES_DB", null);
        String user     = env(dotenv, "POSTGRES_USER", null);
        String password = env(dotenv, "POSTGRES_PASSWORD", null);

        if (database == null || user == null || password == null) {
            throw new IllegalStateException(
                "Variáveis de ambiente POSTGRES_DB, POSTGRES_USER e POSTGRES_PASSWORD são obrigatórias para migrations"
            );
        }

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        System.out.println("[Flyway] Iniciando migrations em: " + url);

        Flyway flyway = Flyway.configure()
            .dataSource(url, user, password)
            // Localização dos arquivos SQL de migration no classpath
            .locations("classpath:db/migration")
            // Cria o schema_history se não existir
            .baselineOnMigrate(true)
            .load();

        var result = flyway.migrate();

        System.out.println("[Flyway] Migrations executadas: " + result.migrationsExecuted);
        System.out.println("[Flyway] Versão atual do schema: " + result.targetSchemaVersion);
    }

    private static String env(Dotenv dotenv, String key, String defaultValue) {
        String fromSystem = System.getenv(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem;
        }
        String fromDotenv = dotenv.get(key);
        if (fromDotenv != null && !fromDotenv.isBlank()) {
            return fromDotenv;
        }
        return defaultValue;
    }
}

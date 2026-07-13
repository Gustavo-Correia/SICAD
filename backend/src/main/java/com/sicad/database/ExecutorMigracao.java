package com.sicad.database;

import io.github.cdimascio.dotenv.Dotenv;
import org.flywaydb.core.Flyway;

public class ExecutorMigracao {

    public static void rodarMigrations() {
        Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

        String host     = dotenv.get("DB_HOST", "localhost");
        String port     = dotenv.get("DB_PORT", "5432");
        String database = dotenv.get("POSTGRES_DB");
        String user     = dotenv.get("POSTGRES_USER");
        String password = dotenv.get("POSTGRES_PASSWORD");

        if (database == null || user == null || password == null) {
            throw new IllegalStateException(
                "Variáveis de ambiente POSTGRES_DB, POSTGRES_USER e POSTGRES_PASSWORD são obrigatórias para migrations"
            );
        }

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        System.out.println("[Flyway] Iniciando migrations em: " + url);

        Flyway flyway = Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();

        var result = flyway.migrate();

        System.out.println("[Flyway] Migrations executadas: " + result.migrationsExecuted);
        System.out.println("[Flyway] Versão atual do schema: " + result.targetSchemaVersion);
    }
}

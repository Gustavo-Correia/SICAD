package com.sicad.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import io.github.cdimascio.dotenv.Dotenv;

public class DatabaseConnection {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private static final String DB_HOST = dotenv.get("DB_HOST", "localhost");
    private static final String DB_PORT = dotenv.get("DB_PORT", "5432");
    private static final String DB_NAME = dotenv.get("DB_NAME");
    private static final String DB_USER = dotenv.get("DB_USER");
    private static final String DB_PASSWORD = dotenv.get("DB_PASSWORD");

    private static final String URL = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    static {
        System.out.println("Database URL: " + URL);
        System.out.println("Database Host: " + DB_HOST);
        System.out.println("Database Port: " + DB_PORT);
        System.out.println("Database Name: " + DB_NAME);
        System.out.println("Database User: " + DB_USER);
        System.out.println("Database Password: " + DB_PASSWORD);
        if (DB_NAME == null || DB_USER == null || DB_PASSWORD == null) {
            throw new IllegalStateException(
                "Variáveis de ambiente POSTGRES_DB, POSTGRES_USER e POSTGRES_PASSWORD são obrigatórias"
            );
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
    }
}

package com.sicad.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "5432");
    private static final String DB_NAME = System.getenv("POSTGRES_DB");
    private static final String DB_USER = System.getenv("POSTGRES_USER");
    private static final String DB_PASSWORD = System.getenv("POSTGRES_PASSWORD");

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

package com.sicad.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ClientService {

    public static void registerClient(String identificador, String enderecoIp) throws SQLException {
        String sql = """
            INSERT INTO clientes (identificador, enderecoip)
            VALUES (?, ?)
            ON CONFLICT (identificador) DO UPDATE
            SET enderecoip = EXCLUDED.enderecoip
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identificador);
            stmt.setString(2, enderecoIp);
            stmt.executeUpdate();
        }
    }

    public static String getClientIp(String identificador) throws SQLException {
        String sql = "SELECT enderecoip FROM clientes WHERE identificador = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identificador);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("enderecoip");
                }
            }
        }
        return null;
    }

    public static String getClientIdByIp(String enderecoIp) throws SQLException {
        String sql = "SELECT identificador FROM clientes WHERE enderecoip = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, enderecoIp);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("identificador");
                }
            }
        }
        return null;
    }
}

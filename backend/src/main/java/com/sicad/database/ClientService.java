package com.sicad.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ClientService {

    private static final Timestamp FAR_FUTURE = Timestamp.from(
        Instant.parse("2099-12-31T23:59:59Z")
    );

    public static void registerClient(String clientId, String ip) throws SQLException {
        String sql = """
            INSERT INTO django_session (session_key, session_data, expire_date)
            VALUES (?, ?, ?)
            ON CONFLICT (session_key) DO UPDATE
            SET session_data = EXCLUDED.session_data,
                expire_date = EXCLUDED.expire_date
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clientId);
            stmt.setString(2, ip);
            stmt.setTimestamp(3, FAR_FUTURE);
            stmt.executeUpdate();
        }
    }

    public static String getClientIp(String clientId) throws SQLException {
        String sql = "SELECT session_data FROM django_session WHERE session_key = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, clientId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("session_data");
                }
            }
        }
        return null;
    }
}

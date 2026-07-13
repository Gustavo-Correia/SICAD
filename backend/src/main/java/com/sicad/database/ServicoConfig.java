package com.sicad.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServicoConfig {

    public static void salvarConfig(String identificador, String host, int porta, int fps,
            double qualidade, int limiteKbps, double escala) throws SQLException {
        String sql = """
            INSERT INTO configuracoes (identificador, host, porta, fps, qualidade, limite_kbps, escala)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (identificador) DO UPDATE SET
                host = EXCLUDED.host,
                porta = EXCLUDED.porta,
                fps = EXCLUDED.fps,
                qualidade = EXCLUDED.qualidade,
                limite_kbps = EXCLUDED.limite_kbps,
                escala = EXCLUDED.escala
            """;

        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identificador);
            stmt.setString(2, host);
            stmt.setInt(3, porta);
            stmt.setInt(4, fps);
            stmt.setDouble(5, qualidade);
            stmt.setInt(6, limiteKbps);
            stmt.setDouble(7, escala);
            stmt.executeUpdate();
        }
    }

    public static String[] carregarConfig(String identificador) throws SQLException {
        String sql = "SELECT host, porta, fps, qualidade, limite_kbps, escala "
                + "FROM configuracoes WHERE identificador = ?";

        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identificador);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                        rs.getString("host"),
                        String.valueOf(rs.getInt("porta")),
                        String.valueOf(rs.getInt("fps")),
                        String.valueOf(rs.getDouble("qualidade")),
                        String.valueOf(rs.getInt("limite_kbps")),
                        String.valueOf(rs.getDouble("escala"))
                    };
                }
            }
        }
        return null;
    }
}

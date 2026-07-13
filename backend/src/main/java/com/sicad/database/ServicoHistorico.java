package com.sicad.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServicoHistorico {
    private static final int LIMITE_HISTORICO = 5;

    public static void adicionarConexao(String usuarioIdentificador, String destinoIdentificador)
            throws SQLException {
        String upsert = """
            INSERT INTO historico_conexoes
                (usuario_identificador, destino_identificador, acessado_em)
            VALUES (?, ?, NOW())
            ON CONFLICT (usuario_identificador, destino_identificador)
            DO UPDATE SET acessado_em = EXCLUDED.acessado_em
            """;
        String removerAntigos = """
            DELETE FROM historico_conexoes
            WHERE usuario_identificador = ?
              AND destino_identificador NOT IN (
                  SELECT destino_identificador
                  FROM historico_conexoes
                  WHERE usuario_identificador = ?
                  ORDER BY acessado_em DESC
                  LIMIT ?
              )
            """;

        try (Connection conn = ConexaoBanco.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(upsert);
                 PreparedStatement limpeza = conn.prepareStatement(removerAntigos)) {
                stmt.setString(1, usuarioIdentificador);
                stmt.setString(2, destinoIdentificador);
                stmt.executeUpdate();

                limpeza.setString(1, usuarioIdentificador);
                limpeza.setString(2, usuarioIdentificador);
                limpeza.setInt(3, LIMITE_HISTORICO);
                limpeza.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static List<String> carregarHistorico(String usuarioIdentificador) throws SQLException {
        String sql = """
            SELECT destino_identificador
            FROM historico_conexoes
            WHERE usuario_identificador = ?
            ORDER BY acessado_em DESC
            LIMIT ?
            """;
        List<String> historico = new ArrayList<>();
        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuarioIdentificador);
            stmt.setInt(2, LIMITE_HISTORICO);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    historico.add(rs.getString("destino_identificador"));
                }
            }
        }
        return historico;
    }
}

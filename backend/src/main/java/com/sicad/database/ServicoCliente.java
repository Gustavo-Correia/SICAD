package com.sicad.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import redis.clients.jedis.Jedis;

public class ServicoCliente {

    private static final int CACHE_TTL_SEGUNDOS = 60;

    public static void registrarCliente(String identificador, String enderecoIp) throws SQLException {
        String sql = """
            INSERT INTO clientes (identificador, enderecoip)
            VALUES (?, ?)
            ON CONFLICT (identificador) DO UPDATE
            SET enderecoip = EXCLUDED.enderecoip
            """;

        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identificador);
            stmt.setString(2, enderecoIp);
            stmt.executeUpdate();
        }

        invalidarCache(identificador, enderecoIp);
    }

    public static String obterIpCliente(String identificador) throws SQLException {
        String cacheKey = "ip:" + identificador;
        try (Jedis jedis = ConexaoRedis.getConnection()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            System.out.println("Redis indisponivel para leitura (ip): " + e.getMessage());
        }

        String sql = "SELECT enderecoip FROM clientes WHERE identificador = ?";

        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, identificador);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String ip = rs.getString("enderecoip");
                    try (Jedis jedis = ConexaoRedis.getConnection()) {
                        jedis.setex(cacheKey, CACHE_TTL_SEGUNDOS, ip);
                    } catch (Exception e) {
                        System.out.println("Redis indisponivel para escrita (ip): " + e.getMessage());
                    }
                    return ip;
                }
            }
        }
        return null;
    }

    public static String obterIdClientePorIp(String enderecoIp) throws SQLException {
        String cacheKey = "id:" + enderecoIp;
        try (Jedis jedis = ConexaoRedis.getConnection()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            System.out.println("Redis indisponivel para leitura (id): " + e.getMessage());
        }

        String sql = "SELECT identificador FROM clientes WHERE enderecoip = ?";

        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, enderecoIp);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("identificador");
                    try (Jedis jedis = ConexaoRedis.getConnection()) {
                        jedis.setex(cacheKey, CACHE_TTL_SEGUNDOS, id);
                    } catch (Exception e) {
                        System.out.println("Redis indisponivel para escrita (id): " + e.getMessage());
                    }
                    return id;
                }
            }
        }
        return null;
    }

    public static int obterContagemDispositivos() throws SQLException {
        String cacheKey = "contagem:dispositivos";
        try (Jedis jedis = ConexaoRedis.getConnection()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
        } catch (Exception e) {
            System.out.println("Redis indisponivel para leitura (contagem): " + e.getMessage());
        }

        String sql = "SELECT COUNT(*) FROM clientes";

        try (Connection conn = ConexaoBanco.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int count = rs.getInt(1);
                try (Jedis jedis = ConexaoRedis.getConnection()) {
                    jedis.setex(cacheKey, CACHE_TTL_SEGUNDOS, String.valueOf(count));
                } catch (Exception e) {
                    System.out.println("Redis indisponivel para escrita (contagem): " + e.getMessage());
                }
                return count;
            }
        }
        return 0;
    }

    private static void invalidarCache(String identificador, String enderecoIp) {
        try (Jedis jedis = ConexaoRedis.getConnection()) {
            jedis.del("ip:" + identificador);
            jedis.del("id:" + enderecoIp);
            jedis.del("contagem:dispositivos");
        } catch (Exception e) {
            System.out.println("Redis indisponivel para invalidacao: " + e.getMessage());
        }
    }
}

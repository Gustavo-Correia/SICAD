package com.sicad.database;

import io.github.cdimascio.dotenv.Dotenv;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class ConexaoRedis {

    private static final Dotenv dotenv = Dotenv.configure()
        .ignoreIfMissing()
        .load();

    private static final String REDIS_HOST = dotenv.get("REDIS_HOST", "redis");
    private static final int REDIS_PORT = Integer.parseInt(dotenv.get("REDIS_PORT", "6379"));
    private static final int TIMEOUT = 2000;

    private static final JedisPool pool;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(5);
        config.setMinIdle(1);
        config.setTestOnBorrow(true);
        pool = new JedisPool(config, REDIS_HOST, REDIS_PORT, TIMEOUT);
    }

    public static Jedis getConnection() {
        return pool.getResource();
    }

    public static void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
}

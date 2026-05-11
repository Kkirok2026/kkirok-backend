package com.database2026.backend.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
public class SqlSupport {

    private final JdbcTemplate jdbcTemplate;

    public SqlSupport(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        Object key = generatedKey(keyHolder);
        if (!(key instanceof Number number)) {
            throw new IllegalStateException("Generated key was not returned.");
        }
        return number.longValue();
    }

    private Object generatedKey(KeyHolder keyHolder) {
        Number singleKey = keyHolder.getKeyList().size() == 1 && keyHolder.getKeys() != null && keyHolder.getKeys().size() == 1
                ? keyHolder.getKey()
                : null;
        if (singleKey != null) {
            return singleKey;
        }
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        return keys.values().iterator().next();
    }
}

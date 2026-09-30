package com.simbirsoft.partners.shared.persistence;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Component
public final class QueryHelper {
    private static final Pattern RESOURCE_PATH = Pattern.compile(
            "sql/(access|partner|lead|staffing|candidate|finance|document|notification|localization|audit|integration)/[a-z0-9-]+\\.sql");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ConcurrentMap<SqlQuery, String> sqlCache = new ConcurrentHashMap<>();

    public QueryHelper(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    public String sql(SqlQuery query) {
        Objects.requireNonNull(query, "query");
        if (!(query instanceof Enum<?>)) {
            throw new IllegalArgumentException("SQL query must be registered by an enum");
        }
        return sqlCache.computeIfAbsent(query, this::loadSql);
    }

    public <T> List<T> query(SqlQuery query, SqlParameterSource parameters, RowMapper<T> rowMapper) {
        return jdbcTemplate.query(sql(query), parameters, rowMapper);
    }

    public <T> T queryForObject(SqlQuery query, SqlParameterSource parameters, Class<T> resultType) {
        return jdbcTemplate.queryForObject(sql(query), parameters, resultType);
    }

    public int update(SqlQuery query, SqlParameterSource parameters) {
        return jdbcTemplate.update(sql(query), parameters);
    }

    private String loadSql(SqlQuery query) {
        String path = query.resourcePath();
        if (path == null || !RESOURCE_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid SQL resource path: " + path);
        }
        try {
            String sql = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).trim();
            if (sql.isEmpty()) {
                throw new IllegalStateException("SQL resource is empty: " + path);
            }
            return sql;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load SQL resource: " + path, exception);
        }
    }
}

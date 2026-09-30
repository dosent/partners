package com.simbirsoft.partners;

import com.simbirsoft.partners.shared.persistence.QueryHelper;
import com.simbirsoft.partners.shared.persistence.SqlQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PostgreSqlStartupTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    QueryHelper queryHelper;

    @Test
    void appliesBaselineChangelogOnCleanPostgreSql() {
        Integer applied = queryHelper.queryForObject(TestQuery.COUNT_BASELINE_CHANGESETS,
                new MapSqlParameterSource("id", "000-initial-schema"), Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void executesRegisteredSqlWithNamedParameters() {
        Integer value = queryHelper.queryForObject(TestQuery.SELECT_VALUE,
                new MapSqlParameterSource("value", 42), Integer.class);
        assertThat(value).isEqualTo(42);
    }

    private enum TestQuery implements SqlQuery {
        SELECT_VALUE("sql/partner/select-value.sql"),
        COUNT_BASELINE_CHANGESETS("sql/partner/count-baseline-changesets.sql");

        private final String resourcePath;

        TestQuery(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        @Override
        public String resourcePath() {
            return resourcePath;
        }
    }
}

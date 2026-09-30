package com.simbirsoft.partners.shared.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class QueryHelperTest {
    private final QueryHelper helper = new QueryHelper(mock(NamedParameterJdbcTemplate.class));

    @Test
    void loadsSqlRegisteredByEnumFromModuleDirectory() {
        assertThat(helper.sql(TestQuery.SELECT_VALUE)).isEqualTo("select :value as value");
    }

    @Test
    void rejectsPathOutsideModuleSqlDirectory() {
        assertThatThrownBy(() -> helper.sql(TestQuery.INVALID_PATH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsClearlyWhenRegisteredSqlIsMissing() {
        assertThatThrownBy(() -> helper.sql(TestQuery.MISSING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sql/partner/missing.sql");
    }

    private enum TestQuery implements SqlQuery {
        SELECT_VALUE("sql/partner/select-value.sql"),
        INVALID_PATH("../secrets.sql"),
        MISSING("sql/partner/missing.sql");

        private final String path;

        TestQuery(String path) {
            this.path = path;
        }

        @Override
        public String resourcePath() {
            return path;
        }
    }
}

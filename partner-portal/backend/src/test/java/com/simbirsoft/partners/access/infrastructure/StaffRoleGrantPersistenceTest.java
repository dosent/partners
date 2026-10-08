package com.simbirsoft.partners.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.simbirsoft.partners.access.application.StaffRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class StaffRoleGrantPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired StaffRoleGrantRepository repository;

    @Test
    @Transactional
    void findsOnlyGrantsForRoleInUsernameOrder() {
        repository.saveAll(List.of(
                new StaffRoleGrantRecord("zoe.smith", StaffRole.RESOURCE_MANAGER),
                new StaffRoleGrantRecord("alice.jones", StaffRole.RESOURCE_MANAGER),
                new StaffRoleGrantRecord("finance.controller", StaffRole.FINANCE_CONTROLLER)));
        repository.flush();

        assertThat(repository.findAllByRoleOrderByUsernameAsc(StaffRole.RESOURCE_MANAGER))
                .extracting(StaffRoleGrantRecord::username)
                .containsExactly("alice.jones", "zoe.smith");
    }

    @Test
    @Transactional
    void returnsNoGrantsWhenRequestedRoleIsNotAssigned() {
        repository.save(new StaffRoleGrantRecord("resource.manager", StaffRole.RESOURCE_MANAGER));
        repository.flush();

        assertThat(repository.findAllByRoleOrderByUsernameAsc(StaffRole.FINANCE_CONTROLLER)).isEmpty();
    }
}

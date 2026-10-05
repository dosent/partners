package com.simbirsoft.partners.mcp.health;

import com.simbirsoft.partners.mcp.backend.BackendApiClient;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog.ToolCatalog;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("mcpReadiness")
public class McpHealthController implements HealthIndicator {
    private final BackendApiClient backend;
    private final ToolCatalog catalog;
    public McpHealthController(BackendApiClient backend,ToolCatalog catalog) { this.backend=backend; this.catalog=catalog; }
    @Override public Health health() {
        boolean catalogValid=catalog!=null&&!catalog.tools().isEmpty();
        boolean backendAvailable=backend.backendHealthy();
        if(!catalogValid||!backendAvailable) return Health.down().withDetail("catalogValid",catalogValid)
                .withDetail("backendAvailable",backendAvailable).build();
        return Health.up().withDetail("catalogToolCount",catalog.tools().size()).build();
    }
}

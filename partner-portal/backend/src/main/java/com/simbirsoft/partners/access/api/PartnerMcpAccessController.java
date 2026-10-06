package com.simbirsoft.partners.access.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner/mcp")
public class PartnerMcpAccessController {
    @GetMapping("/access") @Operation(summary = "Проверить MCP API-ключ")
    public PartnerMcpAccessResponse check() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return new PartnerMcpAccessResponse((java.util.UUID) authentication.getDetails(), true);
    }
}

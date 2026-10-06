package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.PartnerApiKeyService;
import com.simbirsoft.partners.access.application.PartnerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner/api-keys")
public class PartnerApiKeyController {
    private final PartnerApiKeyService service;
    public PartnerApiKeyController(PartnerApiKeyService service) { this.service=service; }
    @GetMapping @Operation(summary = "Получить API-ключи текущего партнёра")
    public List<PartnerApiKeyResponse> list(@AuthenticationPrincipal PartnerPrincipal owner) {
        return service.list(owner).stream().map(PartnerApiKeyResponse::from).toList();
    }
    @PostMapping @Operation(summary = "Создать MCP API-ключ")
    public CreatedPartnerApiKeyResponse create(@AuthenticationPrincipal PartnerPrincipal owner,
            @Valid @RequestBody CreatePartnerApiKeyRequest request) {
        return CreatedPartnerApiKeyResponse.from(service.create(owner, request.name()));
    }
    @DeleteMapping("/{keyId}") @Operation(summary = "Отозвать MCP API-ключ")
    public void revoke(@AuthenticationPrincipal PartnerPrincipal owner, @PathVariable UUID keyId) { service.revoke(owner, keyId); }
    public record CreatePartnerApiKeyRequest(@NotBlank @Size(max=100) String name) { }
}

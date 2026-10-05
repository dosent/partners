package com.simbirsoft.partners.staffing.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.staffing.application.StaffingRequestAttachmentPort;
import com.simbirsoft.partners.staffing.application.StaffingRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.simbirsoft.partners.shared.api.FieldValidationException;

@RestController
@Profile("!auth-test")
@RequestMapping("/api/v1/partner/staffing-requests")
@Tag(name="Staffing requests", description="Заявки партнёра на специалистов")
public class PartnerStaffingRequestController {
    private final StaffingRequestService service;
    private final StaffingRequestAttachmentPort attachments;
    public PartnerStaffingRequestController(StaffingRequestService service, StaffingRequestAttachmentPort attachments) {
        this.service=service; this.attachments=attachments;
    }

    @PostMapping
    @Operation(summary="Создать заявку на специалиста")
    public StaffingRequestResponse create(@AuthenticationPrincipal PartnerPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CreateStaffingRequestRequest request) {
        try { return StaffingRequestResponse.from(service.create(principal, key, request.command())); }
        catch (IllegalArgumentException invalid) {
            String message = invalid.getMessage() == null ? "" : invalid.getMessage().toLowerCase(java.util.Locale.ROOT);
            String field = message.contains("taskdescription") ? "role.taskDescription"
                    : message.contains("preferred") ? "role.preferredSkills"
                    : message.contains("grade") ? "role.grade"
                    : message.contains("required skills") || message.contains("skill") ? "role.requiredSkills"
                    : message.contains("headcount") ? "role.headcount"
                    : message.contains("hours") ? "work.hoursPerWeek"
                    : message.contains("location") ? "work.location"
                    : message.contains("date") ? "work.desiredStartDate"
                    : message.contains("language") ? "selection.languageLevel" : "role.title";
            throw new FieldValidationException("VALIDATION_ERROR", field, "Invalid value");
        }
    }

    @GetMapping
    @Operation(summary="Получить свои заявки")
    public StaffingRequestPageResponse list(@AuthenticationPrincipal PartnerPrincipal principal,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return StaffingRequestPageResponse.from(service.list(principal, page, size), page, size);
    }

    @GetMapping("/{requestId}")
    @Operation(summary="Получить свою заявку")
    public StaffingRequestResponse get(@AuthenticationPrincipal PartnerPrincipal principal, @PathVariable UUID requestId) {
        var request = service.get(principal, requestId);
        return StaffingRequestResponse.from(request, attachments.list(service.ownerId(principal), requestId));
    }

    @PostMapping(value="/{requestId}/attachments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary="Добавить проверенное вложение к сохранённой заявке")
    public StaffingRequestAttachmentPort.Attachment upload(@AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable UUID requestId, @RequestParam String kind, @RequestPart("file") MultipartFile file) {
        service.get(principal, requestId);
        if (!"sourceBrief".equals(kind) && !"cv".equals(kind))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment kind is invalid");
        if (file == null || file.isEmpty() || file.getSize() > 20L*1024*1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment is invalid");
        try { return attachments.upload(service.ownerId(principal), requestId, kind, file.getOriginalFilename(), file.getContentType(), file.getBytes()); }
        catch (IOException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment could not be read"); }
    }

    @GetMapping("/{requestId}/attachments/{attachmentId}")
    @Operation(summary="Скачать своё проверенное вложение")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable UUID requestId, @PathVariable UUID attachmentId) {
        service.get(principal, requestId);
        var file=attachments.download(service.ownerId(principal), requestId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(file.content());
    }
}

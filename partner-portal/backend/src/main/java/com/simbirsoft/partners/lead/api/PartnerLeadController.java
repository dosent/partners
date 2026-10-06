package com.simbirsoft.partners.lead.api;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.lead.application.LeadAttachmentPort;
import com.simbirsoft.partners.lead.application.LeadService;
import com.simbirsoft.partners.shared.api.McpTool;
import com.simbirsoft.partners.shared.api.McpToolBehavior;
import com.simbirsoft.partners.lead.domain.LeadMessage;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;

@RestController
@Profile("!auth-test")
@RequestMapping("/api/v1/partner/leads")
@Tag(name="Partner leads",description="Собственные лиды партнёра")
public class PartnerLeadController {
    private final LeadService service;
    private final LeadAttachmentPort attachments;
    public PartnerLeadController(LeadService service, LeadAttachmentPort attachments) { this.service=service; this.attachments=attachments; }

    @McpTool(name="partner_leads_board", description="List the authenticated partner leads by status", behavior=McpToolBehavior.READ_ONLY)
    @GetMapping(value = "/board", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Загрузить партнёрскую Kanban-доску")
    public LeadBoardResponse board(@AuthenticationPrincipal PartnerPrincipal principal,
            @RequestParam(required=false) String query,@RequestParam(required=false) Instant updatedFrom,
            @RequestParam(required=false) Instant updatedTo,@RequestParam(required=false) LeadStatus status,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limitPerColumn,
            @Parameter(hidden=true) @RequestParam Map<String,String> parameters) {
        String[] cursors=new String[6];
        for (var entry:parameters.entrySet()) if (entry.getKey().startsWith("cursor.")) {
            try { cursors[com.simbirsoft.partners.lead.domain.LeadLane.valueOf(entry.getKey().substring(7)).ordinal()]=entry.getValue(); }
            catch (RuntimeException badLane) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid cursor lane"); }
        }
        return LeadBoardResponse.from(service.board(principal,query,updatedFrom,updatedTo,status,limitPerColumn,java.util.Arrays.asList(cursors)));
    }

    @McpTool(name="partner_lead_create", description="Create a partner lead from structured fields", behavior=McpToolBehavior.MUTATING)
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Создать партнёрский лид")
    public LeadDetailResponse create(@AuthenticationPrincipal PartnerPrincipal principal,@Valid @RequestBody CreateLeadRequest request) {
        var lead=service.create(principal,request.command());
        return LeadDetailResponse.from(lead,service.statusHistory(principal,lead.id()),java.util.List.of());
    }

    @McpTool(name="partner_lead_create_with_attachment", description="Create a partner lead and attach a supported document", behavior=McpToolBehavior.MUTATING)
    @PostMapping(value = "/with-attachment", consumes=MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Создать партнёрский лид с проверяемым вложением")
    public LeadDetailResponse createWithAttachment(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @RequestPart("request") CreateLeadRequest request,
            @RequestPart(value="attachment", required=false) MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) return create(principal, request);
        if (attachment.getSize() > LeadAttachmentPort.MAX_FILE_SIZE_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Attachment must be a PDF or DOCX file up to 20 MB");
        }
        var prepared = attachments.prepare(attachment.getOriginalFilename(), attachment.getContentType(), read(attachment));
        var lead = service.create(principal, request.command(), saved -> attachments.persist(
                service.profileId(principal), saved.id(), prepared));
        var files = attachments.forLead(lead.id()).stream().map(this::attachmentResponse).toList();
        return LeadDetailResponse.from(lead, service.statusHistory(principal, lead.id()), service.messages(principal, lead.id()), files);
    }

    @Hidden
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public LeadDetailResponse createWithAttachmentCompatibility(@AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @RequestPart("request") CreateLeadRequest request,
            @RequestPart(value="attachment", required=false) MultipartFile attachment) {
        return createWithAttachment(principal, request, attachment);
    }

    @McpTool(name="partner_lead_get", description="Get one owned partner lead with its history", behavior=McpToolBehavior.READ_ONLY)
    @GetMapping(value = "/{leadId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Получить детали собственного лида")
    public LeadDetailResponse detail(@AuthenticationPrincipal PartnerPrincipal principal,@PathVariable UUID leadId) {
        var lead=service.find(principal,leadId);
        var files = attachments.forLead(leadId).stream().map(this::attachmentResponse).toList();
        return LeadDetailResponse.from(lead,service.statusHistory(principal,leadId),service.messages(principal,leadId),files);
    }

    @McpTool(name="partner_lead_attachment_download", description="Read an owned partner lead attachment", behavior=McpToolBehavior.READ_ONLY)
    @GetMapping(value = "/{leadId}/attachments/{attachmentId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary="Скачать своё проверенное вложение к лиду")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable UUID leadId, @PathVariable UUID attachmentId) {
        UUID owner = service.profileId(principal);
        service.find(principal, leadId);
        var file = attachments.download(leadId, attachmentId, owner);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(file.content());
    }

    @McpTool(name="partner_lead_message", description="Send a message about an owned partner lead", behavior=McpToolBehavior.MUTATING)
    @PostMapping(value = "/{leadId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Отправить партнёрское сообщение менеджеру")
    public LeadMessageResponse message(@AuthenticationPrincipal PartnerPrincipal principal,@PathVariable UUID leadId,
            @Valid @RequestBody LeadMessageRequest request) {
        return LeadMessageResponse.from(service.sendMessage(principal,leadId,request.body()));
    }

    private LeadDetailResponse.Attachment attachmentResponse(LeadAttachmentPort.Attachment file) {
        return new LeadDetailResponse.Attachment(file.id(), file.fileName(), file.scanStatus(),
                "/api/v1/partner/leads/" + file.leadId() + "/attachments/" + file.id());
    }
    private static byte[] read(MultipartFile file) {
        try { return file.getBytes(); }
        catch (java.io.IOException failure) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Attachment could not be read"); }
    }
}

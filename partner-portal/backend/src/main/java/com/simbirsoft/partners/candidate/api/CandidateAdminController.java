package com.simbirsoft.partners.candidate.api;
import com.simbirsoft.partners.access.application.StaffPrincipal;
import com.simbirsoft.partners.candidate.application.CandidateService;
import com.simbirsoft.partners.candidate.domain.CandidateAvailability;
import com.simbirsoft.partners.shared.api.FieldValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @Profile("!auth-test")
@RequestMapping("/api/v1/admin/candidates")
@Tag(name="Candidates",description="Ручной внутренний каталог кандидатов, CV и часовых ставок")
@ApiResponses({@ApiResponse(responseCode="401",description="Требуется сессия сотрудника"),@ApiResponse(responseCode="403",description="Требуется candidate.manage"),
    @ApiResponse(responseCode="400",description="Ошибка полей"),@ApiResponse(responseCode="409",description="Конфликт версии, ключа Jira или повторной операции")})
public class CandidateAdminController {
    private final CandidateService service;
    public CandidateAdminController(CandidateService service) {this.service=service;}
    @GetMapping @Operation(summary="Найти кандидатов по роли, навыкам, описанию, коду или внешнему ключу")
    public CandidatePageResponse page(@AuthenticationPrincipal StaffPrincipal actor,
            @RequestParam(defaultValue="") @Size(max=200) String q,
            @Parameter(description="AVAILABLE, UNAVAILABLE или ALL") @RequestParam(defaultValue="AVAILABLE") String availability,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        CandidateAvailability selected;
        try { selected="ALL".equals(availability)?null:CandidateAvailability.valueOf(availability); }
        catch(IllegalArgumentException e) {throw new FieldValidationException("VALIDATION_ERROR","availability","Invalid availability");}
        return CandidatePageResponse.from(service.page(actor,q,selected,page,size));
    }
    @GetMapping("/{id}") @Operation(summary="Открыть внутренний профиль кандидата")
    public CandidateResponse get(@AuthenticationPrincipal StaffPrincipal actor,@PathVariable UUID id) {return CandidateResponse.from(service.get(actor,id));}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Operation(summary="Создать кандидата со ставкой и проверенным CV")
    @ApiResponses({@ApiResponse(responseCode="201",description="Кандидат сохранён"),@ApiResponse(responseCode="413",description="CV больше 20 МБ"),@ApiResponse(responseCode="422",description="CV не прошёл проверку"),@ApiResponse(responseCode="503",description="Хранилище или проверка недоступны")})
    public ResponseEntity<CandidateResponse> create(@AuthenticationPrincipal StaffPrincipal actor,@RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestPart("profile") CandidateRequest profile,@RequestPart("cv") MultipartFile cv) throws IOException {
        var result=CandidateResponse.from(service.create(actor,key,profile.command(),cv.getOriginalFilename(),cv.getContentType(),cv.getBytes()));
        return ResponseEntity.created(URI.create("/api/v1/admin/candidates/"+result.id())).body(result);
    }
    @PutMapping("/{id}") @Operation(summary="Изменить профиль, доступность, ключ Jira и ставку кандидата")
    public CandidateResponse update(@AuthenticationPrincipal StaffPrincipal actor,@PathVariable UUID id,@Valid @RequestBody CandidateRequest request) {
        return CandidateResponse.from(service.update(actor,id,request.requiredVersion(),request.command()));
    }
    @PutMapping(value="/{id}/cv",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @Operation(summary="Заменить CV проверенной версией")
    public CandidateResponse replaceCv(@AuthenticationPrincipal StaffPrincipal actor,@PathVariable UUID id,
            @RequestParam @Min(0) long expectedVersion,@RequestPart("cv") MultipartFile cv) throws IOException {
        return CandidateResponse.from(service.replaceCv(actor,id,expectedVersion,cv.getOriginalFilename(),cv.getContentType(),cv.getBytes()));
    }
    @GetMapping("/{id}/cv") @Operation(summary="Скачать текущую проверенную версию CV")
    public ResponseEntity<byte[]> downloadCv(@AuthenticationPrincipal StaffPrincipal actor,@PathVariable UUID id) {
        var file=service.downloadCv(actor,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(file.bytes());
    }
}

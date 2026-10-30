package com.simbirsoft.partners.candidate.api;
import com.simbirsoft.partners.access.application.*;
import com.simbirsoft.partners.access.infrastructure.*;
import com.simbirsoft.partners.candidate.application.*;
import com.simbirsoft.partners.candidate.domain.*;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import com.simbirsoft.partners.document.application.CandidateCv;
import com.simbirsoft.partners.shared.api.FieldValidationException;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(CandidateAdminController.class) @Import(SecurityConfiguration.class)
class CandidateAdminMockMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean CandidateService service;
    @MockitoBean StaffAccessService staff;
    @MockitoBean PartnerApiKeyService apiKeys;
    private final UUID id=UUID.randomUUID();
    private final String payload="""
        {"role":"Java","skills":["Java"],"experienceMonths":0,"description":"Backend","availability":"AVAILABLE","externalKey":"JIRA-1","hourlyRate":{"amount":"2500.50","currency":"RUB"},"expectedVersion":0}
        """;
    private CandidateDetails details() {
        var now=Instant.EPOCH;
        return new CandidateDetails(new CandidateProfile(id,"C-001","Java",null,List.of("Java"),0,"Backend",CandidateAvailability.AVAILABLE,"JIRA-1",now,now,"actor","actor",0),
            new CandidateHourlyRate(id,new BigDecimal("2500.50"),"RUB",now,"actor",0),
            new CandidateCv.Metadata(UUID.randomUUID(),1,"cv.pdf","application/pdf",20,"a".repeat(64),now));
    }
    private StaffAuthenticationToken actor() {return new StaffAuthenticationToken("actor",Set.of(StaffRole.RESOURCE_MANAGER));}
    @BeforeEach void setup() {
        when(staff.rolesFor("actor")).thenReturn(Set.of(StaffRole.RESOURCE_MANAGER));
        when(staff.rolesFor("viewer")).thenReturn(Set.of(StaffRole.PARTNER_MANAGER));
    }
    @Test void serializesRateAsStringAndOmitsStorageKey() throws Exception {
        when(service.get(any(),eq(id))).thenReturn(details());
        mvc.perform(get("/api/v1/admin/candidates/"+id).with(authentication(actor())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hourlyRate.amount").value("2500.50"))
            .andExpect(jsonPath("$.externalKey").value("JIRA-1")).andExpect(jsonPath("$.cv.objectKey").doesNotExist());
    }
    @Test void createsMultipartCandidateAndChecksCsrf() throws Exception {
        var profile=new MockMultipartFile("profile","","application/json",payload.getBytes());
        var file=new MockMultipartFile("cv","cv.pdf","application/pdf","%PDF-1.4".getBytes());
        when(service.create(any(),any(),any(),anyString(),anyString(),any())).thenReturn(details());
        mvc.perform(multipart("/api/v1/admin/candidates").file(profile).file(file).header("Idempotency-Key",UUID.randomUUID()).with(authentication(actor())).with(csrf()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.experienceMonths").value(0));
        mvc.perform(multipart("/api/v1/admin/candidates").file(profile).file(file).header("Idempotency-Key",UUID.randomUUID()).with(authentication(actor())))
            .andExpect(status().isForbidden());
    }
    @Test void forbidsUnauthorizedUsersAndRejectsInvalidInput() throws Exception {
        mvc.perform(get("/api/v1/admin/candidates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/candidates").with(authentication(new StaffAuthenticationToken("viewer",Set.of(StaffRole.PARTNER_MANAGER)))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/candidates?size=101").with(authentication(actor()))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/candidates/"+id).with(authentication(actor())).with(csrf()).contentType("application/json").content(payload.replace("2500.50","1.001")))
            .andExpect(status().isBadRequest());
    }
    @Test void duplicateExternalKeyHasFieldError() throws Exception {
        when(service.update(any(),eq(id),eq(0L),any())).thenThrow(new FieldValidationException(HttpStatus.CONFLICT,"EXTERNAL_KEY_CONFLICT","externalKey","Already linked"));
        mvc.perform(put("/api/v1/admin/candidates/"+id).with(authentication(actor())).with(csrf()).contentType("application/json").content(payload))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors[0].field").value("externalKey"));
    }
}

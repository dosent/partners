package com.simbirsoft.partners.candidate.infrastructure;
import com.simbirsoft.partners.access.application.*;
import com.simbirsoft.partners.candidate.application.*;
import com.simbirsoft.partners.candidate.domain.*;
import com.simbirsoft.partners.document.application.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SpringBootTest @Testcontainers
class CandidateTransactionTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired CandidateService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAccessService roles;
    @MockitoBean ObjectStorage storage;
    @MockitoBean MalwareScanner scanner;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.simbirsoft.partners.document.infrastructure.CandidateCvAdapter cvAdapter;
    private final Map<String,byte[]> objects=new ConcurrentHashMap<>();
    private final StaffPrincipal actor=new StaffPrincipal("resource");
    private final byte[] pdf="%PDF-1.4 artificial CV".getBytes();
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM candidate_cv");jdbc.update("DELETE FROM candidate_hourly_rate");jdbc.update("DELETE FROM candidate_creation");jdbc.update("DELETE FROM candidate_profile");
        objects.clear();when(roles.hasPermission("resource","candidate.manage")).thenReturn(true);
        when(scanner.scan(any())).thenReturn(MalwareScanner.Result.CLEAN);
        doAnswer(a->{objects.put(a.getArgument(0),((byte[])a.getArgument(1)).clone());return null;}).when(storage).put(anyString(),any(),anyString());
        when(storage.get(anyString())).thenAnswer(a->objects.get(a.getArgument(0)));
        doAnswer(a->{objects.remove(a.getArgument(0));return null;}).when(storage).delete(anyString());
    }
    private CandidateCommand command(String key,String amount) { return new CandidateCommand("Java",null,List.of("Java"),0,"Backend",CandidateAvailability.AVAILABLE,key,new BigDecimal(amount),"RUB"); }
    private CandidateDetails create(UUID key,String external) { return service.create(actor,key,command(external,"2500.50"),"cv.pdf","application/pdf",pdf); }
    @Test void persistsProfileRateCvAndReplaysAfterEditWithoutDuplication() {
        var key=UUID.randomUUID();var a=create(key,"JIRA-1");
        assertThat(a.hourlyRate().amount()).isEqualByComparingTo("2500.50");
        assertThat(service.downloadCv(actor,a.profile().id()).bytes()).isEqualTo(pdf);
        var updated=service.update(actor,a.profile().id(),a.profile().version(),command("JIRA-2","3000.00"));
        assertThat(updated.profile().version()).isGreaterThan(a.profile().version());
        assertThat(create(key,"JIRA-1").profile().id()).isEqualTo(a.profile().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_profile",Integer.class)).isEqualTo(1);
        assertThat(objects).hasSize(1);
        assertThatThrownBy(()->create(key,"changed")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void duplicateKeyAndStaleVersionLeavePreviousRateAndCvUntouched() {
        var a=create(UUID.randomUUID(),"JIRA-1");var b=create(UUID.randomUUID(),null);
        assertThatThrownBy(()->service.update(actor,b.profile().id(),b.profile().version(),command("JIRA-1","7000")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(service.get(actor,b.profile().id()).hourlyRate().amount()).isEqualByComparingTo("2500.50");
        var next=service.update(actor,a.profile().id(),a.profile().version(),command(null,"3000"));
        assertThat(next.profile().externalKey()).isNull();assertThat(next.profile().id()).isEqualTo(a.profile().id());
        assertThatThrownBy(()->service.update(actor,a.profile().id(),a.profile().version(),command(null,"4000")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(objects).hasSize(2);
    }
    @Test void replacesCvWithNewVersionAndPreservesOldVersionOnScannerFailure() {
        var a=create(UUID.randomUUID(),null);
        var changed=service.replaceCv(actor,a.profile().id(),a.profile().version(),"new.pdf","application/pdf","%PDF-1.4 changed".getBytes());
        assertThat(changed.cv().version()).isEqualTo(2);assertThat(objects).hasSize(2);
        when(scanner.scan(any())).thenReturn(MalwareScanner.Result.INFECTED);
        assertThatThrownBy(()->service.replaceCv(actor,a.profile().id(),changed.profile().version(),"bad.pdf","application/pdf",pdf)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.downloadCv(actor,a.profile().id()).bytes()).isEqualTo("%PDF-1.4 changed".getBytes());
    }
    @Test void scannerOrStorageFailureDoesNotCreatePartialCandidate() {
        when(scanner.scan(any())).thenThrow(new IllegalStateException());
        assertThatThrownBy(()->create(UUID.randomUUID(),null)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_profile",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_creation",Integer.class)).isZero();
        doReturn(MalwareScanner.Result.CLEAN).when(scanner).scan(any());
        doThrow(new IllegalStateException()).when(storage).put(anyString(),any(),anyString());
        assertThatThrownBy(()->create(UUID.randomUUID(),null)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_hourly_rate",Integer.class)).isZero();
    }
    @Test void refusesUnauthorizedActorBeforeDataAccess() {
        assertThatThrownBy(()->service.page(new StaffPrincipal("viewer"),"",null,0,20)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(403));
    }
    @Test void concurrentSameIdempotencyKeyCreatesOneCandidate() throws Exception {
        var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->create(key,"same"));var b=pool.submit(()->create(key,"same"));
            assertThat(a.get(15,TimeUnit.SECONDS).profile().id()).isEqualTo(b.get(15,TimeUnit.SECONDS).profile().id());
            assertThat(objects).hasSize(1);
        } finally {pool.shutdownNow();}
    }

    @Test void concurrentExternalKeyCreatesOnlyOneCompleteCandidate() throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        Callable<Integer> operation=()->{try {create(UUID.randomUUID(),"race-key");return 201;} catch(ResponseStatusException e){return e.getStatusCode().value();}};
        try {
            var first=pool.submit(operation);var second=pool.submit(operation);
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_profile",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_hourly_rate",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_creation",Integer.class)).isEqualTo(1);
            assertThat(objects).hasSize(1);
        } finally {pool.shutdownNow();}
    }
    @Test void failureAfterCvUploadRollsBackAllModulesAndDeletesObject() {
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Synthetic metadata failure");})
            .when(cvAdapter).store(any(),any(),anyString(),any());
        assertThatThrownBy(()->create(UUID.randomUUID(),null)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_profile",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_hourly_rate",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_cv",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_creation",Integer.class)).isZero();
        assertThat(objects).isEmpty();
    }

    @Test void rejectedCreateExplicitlyProvesNoCandidateWasSavedForRecovery() {
        var key=UUID.randomUUID();
        assertThatThrownBy(()->service.create(actor,key,command(null,"2500.50"),"cv.pdf","application/pdf","Invalid PDF".getBytes()))
            .isInstanceOfSatisfying(com.simbirsoft.partners.shared.api.FieldValidationException.class,
                e->assertThat(e.getCode()).isEqualTo("CANDIDATE_CREATE_REJECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_creation",Integer.class)).isZero();
        assertThat(create(key,null).profile().id()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM candidate_profile",Integer.class)).isEqualTo(1);
    }
}

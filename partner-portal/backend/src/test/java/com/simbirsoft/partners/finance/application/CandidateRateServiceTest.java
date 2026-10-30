package com.simbirsoft.partners.finance.application;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CandidateRateServiceTest {
    @Test void recordsActorAndTimeAndReturnsBatchOfCurrentRates() {
        var values=new HashMap<UUID,CandidateHourlyRate>();
        CandidateRateStore store=new CandidateRateStore() {
            public Map<UUID,CandidateHourlyRate> findAll(Set<UUID> ids) {
                var selected=new HashMap<UUID,CandidateHourlyRate>();ids.forEach(id->{if(values.containsKey(id))selected.put(id,values.get(id));});return selected;
            }
            public CandidateHourlyRate save(CandidateHourlyRate r) {values.put(r.candidateId(),r);return r;}
        };
        var service=new CandidateRateService(store);var id=UUID.randomUUID();
        var rate=service.set(id,new BigDecimal("2500.50"),"RUB","resource",Instant.EPOCH);
        assertThat(rate.changedBy()).isEqualTo("resource");assertThat(rate.changedAt()).isEqualTo(Instant.EPOCH);
        assertThat(service.findAll(Set.of(id)).get(id).amount()).isEqualByComparingTo("2500.50");
        assertThat(service.findAll(Set.of())).isEmpty();
        var unchanged=service.set(id,new BigDecimal("2500.5"),"RUB","other",Instant.EPOCH.plusSeconds(60));
        assertThat(unchanged.changedAt()).isEqualTo(Instant.EPOCH);
        assertThat(unchanged.changedBy()).isEqualTo("resource");
        assertThat(unchanged.version()).isEqualTo(rate.version());
        var changed=service.set(id,new BigDecimal("3000.00"),"RUB","other",Instant.EPOCH.plusSeconds(120));
        assertThat(changed.changedAt()).isEqualTo(Instant.EPOCH.plusSeconds(120));
        assertThat(changed.changedBy()).isEqualTo("other");
    }
}

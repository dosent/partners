package com.simbirsoft.partners.finance.infrastructure;
import com.simbirsoft.partners.finance.application.CandidateRateStore;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
@Repository @Profile("!auth-test")
public class JpaCandidateRateStore implements CandidateRateStore {
    private final CandidateHourlyRateJpaRepository rows;
    public JpaCandidateRateStore(CandidateHourlyRateJpaRepository rows) {this.rows=rows;}
    @Override @Transactional(readOnly=true) public Map<UUID,CandidateHourlyRate> findAll(Set<UUID> ids) {
        return rows.findAllById(ids).stream().map(CandidateHourlyRateRecord::value).collect(Collectors.toMap(CandidateHourlyRate::candidateId,p->p));
    }
    @Override @Transactional public CandidateHourlyRate save(CandidateHourlyRate value) {
        var r=rows.findById(value.candidateId()).orElseGet(CandidateHourlyRateRecord::new);
        r.candidateId=value.candidateId();r.amount=value.amount();r.currency=value.currency();r.changedAt=value.changedAt();r.changedBy=value.changedBy();
        return rows.saveAndFlush(r).value();
    }
}

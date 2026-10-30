package com.simbirsoft.partners.finance.application;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
@Service @Profile("!auth-test")
public class CandidateRateService implements CandidateRates {
    private final CandidateRateStore store;
    public CandidateRateService(CandidateRateStore store) { this.store=store; }
    @Override @Transactional
    public CandidateHourlyRate set(UUID id,BigDecimal amount,String currency,String actor,Instant now) {
        var old=store.findAll(Set.of(id)).get(id);
        var next=new CandidateHourlyRate(id,amount,currency,now,actor,old==null?0:old.version());
        if(old!=null && old.amount().compareTo(next.amount())==0 && old.currency().equals(next.currency())) return old;
        return store.save(next);
    }
    @Override @Transactional(readOnly=true)
    public Map<UUID,CandidateHourlyRate> findAll(Set<UUID> ids) { return ids.isEmpty()?Map.of():store.findAll(ids); }
}

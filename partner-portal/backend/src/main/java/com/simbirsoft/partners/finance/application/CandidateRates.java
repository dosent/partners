package com.simbirsoft.partners.finance.application;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public interface CandidateRates {
    CandidateHourlyRate set(UUID candidateId,BigDecimal amount,String currency,String actor,Instant now);
    Map<UUID,CandidateHourlyRate> findAll(Set<UUID> candidateIds);
}

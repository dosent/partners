package com.simbirsoft.partners.finance.application;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import java.util.*;
public interface CandidateRateStore {
    Map<UUID,CandidateHourlyRate> findAll(Set<UUID> candidateIds);
    CandidateHourlyRate save(CandidateHourlyRate rate);
}

package com.simbirsoft.partners.candidate.application;
import com.simbirsoft.partners.candidate.domain.*;
import java.util.*;
public interface CandidateProfileStore {
    Optional<CandidateProfile> find(UUID id);
    CandidateProfile saveAndFlush(CandidateProfile value);
    Page page(String q,CandidateAvailability availability,int page,int size);
    record Page(List<CandidateProfile> items,int page,int size,long total) {}
}

package com.simbirsoft.partners.candidate.application;
import java.util.UUID;
public interface CandidateCreationStore {
    Reservation reserve(String actor,UUID key,String fingerprint,UUID candidateId);
    record Reservation(UUID candidateId,String fingerprint,boolean created) {}
}

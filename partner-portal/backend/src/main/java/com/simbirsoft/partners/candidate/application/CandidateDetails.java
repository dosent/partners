package com.simbirsoft.partners.candidate.application;
import com.simbirsoft.partners.candidate.domain.CandidateProfile;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import com.simbirsoft.partners.document.application.CandidateCv;
public record CandidateDetails(CandidateProfile profile,CandidateHourlyRate hourlyRate,CandidateCv.Metadata cv) {}

package com.simbirsoft.partners.candidate.application;
import com.simbirsoft.partners.candidate.domain.CandidateAvailability;
import java.util.List;
import java.math.BigDecimal;
public record CandidateCommand(String role,String grade,List<String> skills,int experienceMonths,
    String description,CandidateAvailability availability,String externalKey,BigDecimal amount,String currency) {}

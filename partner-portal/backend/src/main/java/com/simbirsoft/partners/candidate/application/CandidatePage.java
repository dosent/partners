package com.simbirsoft.partners.candidate.application;
import java.util.List;
public record CandidatePage(List<CandidateDetails> items,int page,int size,long total) {}

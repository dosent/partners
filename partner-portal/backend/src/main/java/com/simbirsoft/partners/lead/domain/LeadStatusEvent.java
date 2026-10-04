package com.simbirsoft.partners.lead.domain;
import java.time.Instant;
public record LeadStatusEvent(LeadStatus status,Instant changedAt) { }

package com.simbirsoft.partners.finance.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record CandidateHourlyRate(UUID candidateId, BigDecimal amount, String currency,
        Instant changedAt, String changedBy, long version) {
    public CandidateHourlyRate {
        if(candidateId==null || changedAt==null || changedBy==null || changedBy.isBlank() || version<0)
            throw new IllegalArgumentException("hourlyRate");
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.compareTo(new BigDecimal("9999999999.99"))>0)
            throw new IllegalArgumentException("hourlyRate.amount");
        try { Currency.getInstance(currency); }
        catch(RuntimeException e) { throw new IllegalArgumentException("hourlyRate.currency"); }
        amount=amount.setScale(2);
    }
}

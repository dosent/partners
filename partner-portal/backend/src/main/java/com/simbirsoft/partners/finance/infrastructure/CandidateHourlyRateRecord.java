package com.simbirsoft.partners.finance.infrastructure;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="candidate_hourly_rate")
class CandidateHourlyRateRecord {
    @Id @Column(name="candidate_id") UUID candidateId;
    @Column(nullable=false,precision=12,scale=2) BigDecimal amount;
    @Column(nullable=false,length=3) String currency;
    @Column(name="changed_at",nullable=false) Instant changedAt;
    @Column(name="changed_by",nullable=false) String changedBy;
    @Version Long version;
    CandidateHourlyRate value() {return new CandidateHourlyRate(candidateId,amount,currency,changedAt,changedBy,version==null?0:version);}
}

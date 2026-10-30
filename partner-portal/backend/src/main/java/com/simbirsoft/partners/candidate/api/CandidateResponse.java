package com.simbirsoft.partners.candidate.api;
import com.simbirsoft.partners.candidate.application.CandidateDetails;
import com.simbirsoft.partners.candidate.domain.CandidateAvailability;
import com.simbirsoft.partners.document.application.CandidateCv;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
@Schema(description="Внутренняя карточка кандидата")
public record CandidateResponse(UUID id,String code,String role,String grade,List<String> skills,int experienceMonths,
        String description,CandidateAvailability availability,String externalKey,HourlyRate hourlyRate,CandidateCv.Metadata cv,
        Instant createdAt,Instant updatedAt,long version) {
    static CandidateResponse from(CandidateDetails d) {
        var p=d.profile();var r=d.hourlyRate();
        return new CandidateResponse(p.id(),p.code(),p.role(),p.grade(),p.skills(),p.experienceMonths(),p.description(),p.availability(),p.externalKey(),
            r==null?null:new HourlyRate(r.amount().toPlainString(),r.currency(),r.changedAt(),r.changedBy()),d.cv(),p.createdAt(),p.updatedAt(),p.version());
    }
    @Schema(description="Текущая ставка за час без расчёта экономики")
    public record HourlyRate(@Schema(type="string",example="2500.50") String amount,String currency,Instant changedAt,String changedBy) {}
}

package com.simbirsoft.partners.candidate.api;
import com.simbirsoft.partners.candidate.application.CandidatePage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description="Страница внутреннего каталога кандидатов")
public record CandidatePageResponse(List<CandidateResponse> items,int page,int size,long total) {
    static CandidatePageResponse from(CandidatePage p) {return new CandidatePageResponse(p.items().stream().map(CandidateResponse::from).toList(),p.page(),p.size(),p.total());}
}

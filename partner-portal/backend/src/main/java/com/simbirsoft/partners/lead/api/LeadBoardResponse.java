package com.simbirsoft.partners.lead.api;
import com.simbirsoft.partners.lead.application.LeadService.Board;
import com.simbirsoft.partners.lead.domain.LeadLane;
import com.simbirsoft.partners.lead.domain.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description="Партнёрская Kanban-доска лидов")
public record LeadBoardResponse(List<Column> columns) {
    static LeadBoardResponse from(Board board) { return new LeadBoardResponse(board.columns().stream().map(c -> new Column(c.lane(),c.statuses(),c.status(),c.total(),
            c.items().stream().map(LeadCardResponse::from).toList(),c.nextCursor())).toList()); }
    @Schema(description="Колонка доски")
    public record Column(LeadLane lane,List<LeadStatus> statuses,LeadStatus status,long total,List<LeadCardResponse> items,String nextCursor) { }
}

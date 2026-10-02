package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.shared.persistence.QueryHelper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class PartnerApplicationQueryRepository {
    private final QueryHelper queryHelper;

    public PartnerApplicationQueryRepository(QueryHelper queryHelper) { this.queryHelper = queryHelper; }

    public List<ManualReviewSummary> findManualReviewPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
        long offset = Math.multiplyExact((long) page, size);
        return queryHelper.query(PartnerApplicationQueries.MANUAL_REVIEW_PAGE,
                new MapSqlParameterSource().addValue("offset", offset).addValue("limit", size),
                (rs, row) -> new ManualReviewSummary(rs.getObject("application_id", UUID.class),
                        rs.getTimestamp("submitted_at").toInstant(), rs.getInt("revision_number")));
    }

    public long countManualReview() {
        Long count = queryHelper.queryForObject(PartnerApplicationQueries.MANUAL_REVIEW_COUNT,
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    public record ManualReviewSummary(UUID id, Instant submittedAt, int revisionNumber) { }
}

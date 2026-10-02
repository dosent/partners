package com.simbirsoft.partners.partner.infrastructure;

import com.simbirsoft.partners.shared.persistence.SqlQuery;

public enum PartnerApplicationQueries implements SqlQuery {
    MANUAL_REVIEW_PAGE("sql/partner/manual-review-queue.sql"),
    MANUAL_REVIEW_COUNT("sql/partner/manual-review-queue-count.sql");

    private final String resourcePath;

    PartnerApplicationQueries(String resourcePath) { this.resourcePath = resourcePath; }

    @Override public String resourcePath() { return resourcePath; }
}

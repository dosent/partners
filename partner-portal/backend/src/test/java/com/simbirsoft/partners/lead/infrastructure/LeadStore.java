package com.simbirsoft.partners.lead.infrastructure;

import org.springframework.data.repository.Repository;

public interface LeadStore extends Repository<LeadRecord, Long> {
}

package com.simbirsoft.architecture.fixtures.lead;

import org.springframework.data.repository.Repository;

public interface LeadStore extends Repository<LeadRecord, Long> {
}

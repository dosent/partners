package com.simbirsoft.partners.lead.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class LeadRecord {
    @Id
    private Long id;
}

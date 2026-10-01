package com.simbirsoft.architecture.fixtures.lead;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class LeadRecord {
    @Id
    private Long id;
}

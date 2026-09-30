package com.simbirsoft.partners.partner.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PartnerRecord {
    @Id
    private Long id;
}

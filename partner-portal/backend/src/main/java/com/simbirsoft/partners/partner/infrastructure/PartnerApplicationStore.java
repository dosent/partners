package com.simbirsoft.partners.partner.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PartnerApplicationStore extends JpaRepository<PartnerApplicationRecord, UUID> {
}

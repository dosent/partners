package com.simbirsoft.partners.localization.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "registration_content_current")
class RegistrationContentCurrentRecord {
    @Id
    private String locale;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false, unique = true)
    private RegistrationContentPublicationRecord publication;

    protected RegistrationContentCurrentRecord() { }

    RegistrationContentCurrentRecord(String locale, RegistrationContentPublicationRecord publication) {
        this.locale = locale;
        this.publication = publication;
    }

    RegistrationContentPublicationRecord publication() { return publication; }
}

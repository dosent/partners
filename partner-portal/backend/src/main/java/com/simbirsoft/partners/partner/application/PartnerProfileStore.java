package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.partner.domain.PartnerProfile;
import java.util.Optional;

/** Хранилище активных партнёрских профилей; все чтения ограничиваются внешней идентичностью. */
public interface PartnerProfileStore {
    Optional<PartnerProfile> findByIdentity(String identityProvider, String externalSubject);
    PartnerProfile save(PartnerProfile profile);
}

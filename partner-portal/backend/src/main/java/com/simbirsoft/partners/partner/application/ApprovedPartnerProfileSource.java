package com.simbirsoft.partners.partner.application;

import com.simbirsoft.partners.partner.domain.PartnerProfile;
import com.simbirsoft.partners.partner.domain.PartnerRegistrationData;
import java.util.Optional;

/** Возвращает регистрационные сведения только для профиля, ссылающегося на одобренную редакцию. */
public interface ApprovedPartnerProfileSource {
    Optional<ApprovedProfile> findApprovedProfile(String identityProvider, String externalSubject);
    Optional<PartnerRegistrationData> findApprovedRegistration(PartnerProfile profile);

    record ApprovedProfile(PartnerProfile profile, PartnerRegistrationData registrationData) { }
}

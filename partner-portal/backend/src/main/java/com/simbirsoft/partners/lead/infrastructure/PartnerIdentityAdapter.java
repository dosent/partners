package com.simbirsoft.partners.lead.infrastructure;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import com.simbirsoft.partners.lead.application.LeadPartnerIdentity;
import com.simbirsoft.partners.partner.application.PartnerProfileService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PartnerIdentityAdapter implements LeadPartnerIdentity {
    private final PartnerProfileService profiles;
    PartnerIdentityAdapter(PartnerProfileService profiles) { this.profiles=profiles; }
    @Override public UUID profileId(PartnerPrincipal principal) { return profiles.current(principal).id(); }
}

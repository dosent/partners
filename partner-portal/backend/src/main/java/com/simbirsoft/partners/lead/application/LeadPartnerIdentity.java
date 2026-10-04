package com.simbirsoft.partners.lead.application;

import com.simbirsoft.partners.access.application.PartnerPrincipal;
import java.util.UUID;

public interface LeadPartnerIdentity {
    UUID profileId(PartnerPrincipal principal);
}

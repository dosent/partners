package com.simbirsoft.partners.access.infrastructure;

import com.simbirsoft.partners.access.api.StaffAuthFlow;
import com.simbirsoft.partners.access.application.StaffAccessDeniedException;
import com.simbirsoft.partners.access.application.StaffAuthenticationFailedException;
import com.simbirsoft.partners.access.application.StaffDirectoryUnavailableException;
import com.simbirsoft.partners.access.application.StaffIdentity;
import com.simbirsoft.partners.access.application.StaffLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class LdapStaffAuthFlowAdapter implements StaffAuthFlow {
    private static final Logger log = LoggerFactory.getLogger(LdapStaffAuthFlowAdapter.class);
    private final StaffLoginService loginService;
    private final StaffSessionService sessionService;

    public LdapStaffAuthFlowAdapter(StaffLoginService loginService, StaffSessionService sessionService) {
        this.loginService = loginService;
        this.sessionService = sessionService;
    }

    @Override
    public StaffIdentity login(String username, String password, HttpServletRequest request,
            HttpServletResponse response) {
        try {
            StaffIdentity identity = loginService.authenticate(username, password);
            sessionService.authenticate(identity, request, response);
            return identity;
        } catch (StaffAuthenticationFailedException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff authentication failed");
        } catch (StaffAccessDeniedException exception) {
            log.warn("staff_authentication_denied reason=no_mapped_portal_role");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff portal role is not assigned");
        } catch (StaffDirectoryUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Staff directory is unavailable");
        }
    }
}

package com.simbirsoft.partners.access.api;

import com.simbirsoft.partners.access.application.StaffIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface StaffAuthFlow {
    StaffIdentity login(String username, String password, HttpServletRequest request, HttpServletResponse response);
}

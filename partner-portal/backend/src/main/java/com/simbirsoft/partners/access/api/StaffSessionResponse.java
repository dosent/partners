package com.simbirsoft.partners.access.api;

import java.util.List;

public record StaffSessionResponse(String username, List<String> roles) {
}

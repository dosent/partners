package com.simbirsoft.partners.access.api;

import java.util.List;

public record StaffSessionResponse(String username, String accessState, List<String> roles, List<String> permissions) {
}

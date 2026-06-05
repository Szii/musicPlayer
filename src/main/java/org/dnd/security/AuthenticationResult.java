package org.dnd.security;

import org.dnd.api.model.AuthResponse;

public record AuthenticationResult(
        AuthResponse authResponse,
        String refreshToken
) {
}

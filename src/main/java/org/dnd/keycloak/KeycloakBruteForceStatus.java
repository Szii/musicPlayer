package org.dnd.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakBruteForceStatus(
        boolean disabled,
        long failedLoginNotBefore
) {
}

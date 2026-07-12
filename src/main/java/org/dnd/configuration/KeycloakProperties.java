package org.dnd.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class KeycloakProperties {

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.issuer-uri}")
  private String issuerUri;

  @Value("${keycloak.internal-url}")
  private String internalUrl;

  @Value("${keycloak.jwk-set-uri}")
  private String jwkSetUri;

  @Value("${keycloak.client-id}")
  private String clientId;

  @Value("${keycloak.client-secret}")
  private String clientSecret;

  @Value("${keycloak.admin-client-id}")
  private String adminClientId;

  @Value("${keycloak.admin-client-secret}")
  private String adminClientSecret;

  @Value("${keycloak.token-uri}")
  private String tokenUri;

  @Value("${keycloak.logout-uri}")
  private String logoutUri;

  @Value("${keycloak.revoke-uri}")
  private String revokeUri;

  @Value("${keycloak.admin-users-uri}")
  private String adminUsersUri;

  @Value("${keycloak.admin-brute-force-uri}")
  private String adminBruteForceUri;

  @Value("${keycloak.authorization-uri}")
  private String authorizationUri;

  @Value("${keycloak.google-idp-alias}")
  private String googleIdpAlias;
}

package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Respuesta de /auth/oauth/v2/token de BBVA (se ignoran scope y resource). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthToken(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn) {}

package com.portal.bulkpayments.service;

import com.portal.bulkpayments.client.BbvaAuthConfig;
import com.portal.bulkpayments.client.BbvaOAuthClient;
import com.portal.bulkpayments.dto.OAuthToken;
import com.portal.bulkpayments.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Obtiene el token OAuth de BBVA (client_credentials) y lo cachea hasta 60 s antes de que venza.
 * Nunca registra el token ni el secret.
 */
@ApplicationScoped
public class BbvaTokenProvider {

  private static final Logger LOG = Logger.getLogger(BbvaTokenProvider.class);
  private static final Duration MARGIN = Duration.ofSeconds(60);

  @Inject BbvaAuthConfig auth;
  @Inject @RestClient BbvaOAuthClient client;

  private String authorization;
  private Instant expiresAt = Instant.EPOCH;

  /** Valor de la cabecera Authorization ("Bearer <token>"); pide uno nuevo si no hay o está por vencer. */
  public synchronized String authorization() {
    if (authorization == null || Instant.now().plus(MARGIN).isAfter(expiresAt)) {
      fetch();
    }
    return authorization;
  }

  /** Descarta el token cacheado (BBVA respondió 401): la próxima llamada pide otro. */
  public synchronized void invalidate() {
    authorization = null;
  }

  private void fetch() {
    String clientId = present(auth.clientId());
    String clientSecret = present(auth.clientSecret());
    if (clientId == null || clientSecret == null) {
      throw new ApiException(502, "upstreamAuthFailed", "Credenciales OAuth de BBVA no configuradas.");
    }
    try (Response response = client.token("client_credentials", clientId, clientSecret)) {
      if (response.getStatus() / 100 != 2) {
        LOG.warnf("BBVA token respondió %d", response.getStatus());
        throw new ApiException(502, "upstreamAuthFailed", "No se pudo obtener el token de BBVA.");
      }
      OAuthToken token = response.readEntity(OAuthToken.class);
      if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
        throw new ApiException(502, "upstreamAuthFailed", "No se pudo obtener el token de BBVA.");
      }
      String type = token.tokenType() == null || token.tokenType().isBlank() ? "Bearer" : token.tokenType();
      authorization = type + " " + token.accessToken();
      expiresAt = Instant.now().plusSeconds(token.expiresIn());
      LOG.infof("Token BBVA obtenido; vence en %d s", token.expiresIn());
    } catch (ProcessingException e) {
      throw UpstreamErrors.connectionError("token", e);
    }
  }

  private static String present(Optional<String> value) {
    return value.filter(v -> !v.isBlank()).orElse(null);
  }
}

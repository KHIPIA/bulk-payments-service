package com.portal.bulkpayments.client;

import io.smallrye.config.ConfigMapping;
import java.util.Optional;

/**
 * Credenciales del sandbox BBVA (bbva.auth.*): client-id/client-secret para el token OAuth
 * (BBVA_CLIENT_ID, BBVA_CLIENT_SECRET) y la clave privada con la que BbvaRequestSigner firma cada
 * petición (BBVA_PRIVATE_KEY_BASE64: base64 del PEM). Opcionales para que deferred-signature arranque sin ellas.
 */
@ConfigMapping(prefix = "bbva.auth")
public interface BbvaAuthConfig {

  Optional<String> clientId();

  Optional<String> clientSecret();

  Optional<String> privateKey();
}

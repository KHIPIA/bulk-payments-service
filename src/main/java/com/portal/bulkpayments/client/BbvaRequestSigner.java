package com.portal.bulkpayments.client;

import com.portal.bulkpayments.exception.ApiException;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Firma HTTP de BBVA API Market (port del pre-request de Postman). Por cada llamada calcula:
 * Digest = SHA-512 del cuerpo exacto; Date con el formato de Date.toUTCString(); Host del URL del
 * client; y Signature = RSA-SHA512 sobre "(request-target) host date digest".
 * La clave privada llega en bbva.auth.private-key (BBVA_PRIVATE_KEY_BASE64: base64 del PEM PKCS#8).
 * Nunca registrar la clave, la cadena firmada ni las cabeceras.
 */
@ApplicationScoped
public class BbvaRequestSigner {

  private static final Logger LOG = Logger.getLogger(BbvaRequestSigner.class);

  /** Igual que Date.prototype.toUTCString(): "Sun, 01 Nov 2026 10:00:00 GMT" (día con dos dígitos). */
  static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

  /** Cabeceras que se envían a BBVA en la invocación. */
  public record SignedHeaders(String digest, String signature, String date, String host) {}

  @Inject BbvaAuthConfig auth;

  @ConfigProperty(name = "quarkus.rest-client.bbva-bulk-payments.url")
  String baseUrl;

  Clock clock = Clock.systemUTC();

  private PrivateKey privateKey;

  void warnIfMissingCredentials(@Observes StartupEvent event) {
    if (present(auth.clientId()).isEmpty() || present(auth.clientSecret()).isEmpty()) {
      LOG.warn("BBVA_CLIENT_ID/BBVA_CLIENT_SECRET no configurados; /api/bulk-payments responderá 502 upstreamAuthFailed.");
    }
    if (present(auth.privateKey()).isEmpty()) {
      LOG.warn("BBVA_PRIVATE_KEY_BASE64 no configurada; /api/bulk-payments responderá 502 upstreamAuthFailed.");
    }
  }

  /**
   * @param method método HTTP (se firma en minúsculas)
   * @param relativePath ruta relativa al URL del client, p. ej. "/remittances/{id}"
   * @param body cuerpo exacto que se enviará ("" si no hay)
   */
  public SignedHeaders sign(String method, String relativePath, String body) {
    URI base = URI.create(baseUrl);
    String host = base.getRawAuthority(); // como baseUrl.split('/')[2]: incluye el puerto si lo hay
    String path = (stripTrailingSlash(base.getRawPath()) + relativePath).split("\\?")[0];

    String digest = "SHA-512=" + base64(sha512(body == null ? "" : body));
    String date = DATE_FORMAT.format(ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC));
    String signingString =
        "(request-target):" + method.toLowerCase(Locale.ROOT) + " " + path + "\n"
            + "host:" + host + "\n"
            + "date:" + date + "\n"
            + "digest:" + digest;

    String signature =
        "algorithm=\"rsa-sha512\",headers=\"(request-target) host date digest\",signature=\""
            + base64(rsaSha512(signingString)) + "\"";
    return new SignedHeaders(digest, signature, date, host);
  }

  private byte[] rsaSha512(String data) {
    try {
      Signature signer = Signature.getInstance("SHA512withRSA");
      signer.initSign(privateKey());
      signer.update(data.getBytes(StandardCharsets.UTF_8));
      return signer.sign();
    } catch (GeneralSecurityException e) {
      throw signingFailed();
    }
  }

  private synchronized PrivateKey privateKey() {
    if (privateKey == null) {
      String encoded = present(auth.privateKey()).orElseThrow(() ->
          new ApiException(502, "upstreamAuthFailed", "Clave privada de BBVA no configurada."));
      privateKey = parse(encoded);
    }
    return privateKey;
  }

  /** base64(PEM) → PKCS#8. La cabecera puede decir "RSA PRIVATE KEY": el contenido es PKCS#8, como asume el script. */
  static PrivateKey parse(String base64Pem) {
    try {
      String pem = new String(Base64.getMimeDecoder().decode(base64Pem.trim()), StandardCharsets.UTF_8);
      String body = pem.replaceAll("-----(BEGIN|END) (RSA )?PRIVATE KEY-----", "").replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(body);
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      LOG.warn("BBVA_PRIVATE_KEY_BASE64 no es una clave RSA PKCS#8 válida.");
      throw signingFailed();
    }
  }

  private static ApiException signingFailed() {
    return new ApiException(502, "upstreamAuthFailed", "No se pudo firmar la petición a BBVA.");
  }

  private static byte[] sha512(String body) {
    try {
      return MessageDigest.getInstance("SHA-512").digest(body.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String base64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static String stripTrailingSlash(String path) {
    if (path == null) {
      return "";
    }
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static Optional<String> present(Optional<String> value) {
    return value.filter(v -> !v.isBlank());
  }
}

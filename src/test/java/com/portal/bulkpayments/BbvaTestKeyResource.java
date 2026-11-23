package com.portal.bulkpayments;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

/**
 * Genera un par RSA desechable para los tests: la privada entra como bbva.auth.private-key
 * (base64 del PEM, igual que BBVA_PRIVATE_KEY_BASE64) y la pública queda para verificar la firma.
 * Ninguna clave real en el repo.
 */
public class BbvaTestKeyResource implements QuarkusTestResourceLifecycleManager {

  static final KeyPair KEYS = generate();

  static PublicKey publicKey() {
    return KEYS.getPublic();
  }

  @Override
  public Map<String, String> start() {
    String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
        .encodeToString(KEYS.getPrivate().getEncoded());
    // Cabecera "RSA PRIVATE KEY" con contenido PKCS#8, como la clave del sandbox.
    String pem = "-----BEGIN RSA PRIVATE KEY-----\n" + body + "\n-----END RSA PRIVATE KEY-----\n";
    return Map.of(
        "bbva.auth.private-key",
        Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public void stop() {}

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

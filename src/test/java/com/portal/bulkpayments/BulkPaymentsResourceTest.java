package com.portal.bulkpayments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.HttpHeaders;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

/** /api/bulk-payments reenvía a BBVA; aquí BBVA es FakeBbvaResource (ver %test en application.yml). */
@QuarkusTest
@QuarkusTestResource(BbvaTestKeyResource.class)
class BulkPaymentsResourceTest {

  static final String BASE = "/bulk-payments"; // RestAssured ya antepone quarkus.http.root-path (/api)
  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Payloads de ejemplo del sandbox: el header lleva el total final de la remesa (4 pagos / 12000).
  static final String CREATE = """
      {"name":"Agregar varios pagos soles",
       "header":{"numberOfTransactions":4,"controlSum":[{"amount":12000,"currency":"PEN"}]},
       "status":"DRAFT",
       "remittances":[{"instructionId":"DD43-ID",
         "debtor":{"identity":{"id":"20600380533","documentType":"RUC"}},
         "debtorName":"JV PERFORACIONES EIRL",
         "debtorAccount":{"numberType":"CCC","number":"001101300105109811"},
         "payments":[
           {"endToEndId":"PAGO 1","paymentAmount":{"currency":"PEN","amount":3000},
            "creditor":{"name":"Adam Fulton","identity":{"id":"21211232","documentType":"DNI"}},
            "creditorAccount":{"numberType":"CCC","number":"001108140100035281"},"paymentType":"INTERNAL_PAYMENT"},
           {"endToEndId":"PAGO 2","paymentAmount":{"currency":"PEN","amount":3000},
            "creditor":{"name":"Adam Fulton","identity":{"id":"34211223","documentType":"DNI"}},
            "creditorAccount":{"numberType":"CCC","number":"001108140100026177"},"paymentType":"INTERNAL_PAYMENT"}]}]}
      """;

  static final String PATCH = """
      {"header":{"numberOfTransactions":4,"controlSum":[{"amount":12000,"currency":"PEN"}]},
       "status":"CLOSED",
       "remittances":[{"instructionId":"DD43-ID",
         "debtorAccount":{"numberType":"CCC","number":"001101300105109811"},
         "payments":[
           {"endToEndId":"Pago 3","paymentAmount":{"currency":"PEN","amount":3000},
            "creditor":{"name":"Jacqueline Jackson","identity":{"id":"20234323332","documentType":"RUC"}},
            "creditorAccount":{"numberType":"CCC","number":"001101300210477311"},"paymentType":"INTERNAL_PAYMENT"}]}]}
      """;

  static final String CLOSE = """
      {"remittances":[{"debtorAccount":{"number":"001101300105109811"}}]}
      """;

  @Test
  void createIsForwardedAndReturns201WithLocation() {
    given().contentType(ContentType.JSON).body(CREATE).when().post(BASE + "/remittance")
        .then()
        .statusCode(201)
        .header("Location", "/pe/bulk-payments/v1/remittances/" + FakeBbvaResource.ID)
        .body("data.id", equalTo(FakeBbvaResource.ID))
        .body("data.name", equalTo("Agregar varios pagos soles"));

    assertEquals("/remittance", FakeBbvaResource.lastPath);
    // Campos que los DTO locales no modelan llegan intactos.
    assertEquals("JV PERFORACIONES EIRL", FakeBbvaResource.lastBody.at("/remittances/0/debtorName").asText());
    assertEquals("CCC", FakeBbvaResource.lastBody.at("/remittances/0/debtorAccount/numberType").asText());
    assertEquals("PAGO 1", FakeBbvaResource.lastBody.at("/remittances/0/payments/0/endToEndId").asText());
  }

  @Test
  void securityHeadersAreSignedPerRequest() throws Exception {
    given().contentType(ContentType.JSON).body(CREATE).when().post(BASE + "/remittance").then().statusCode(201);

    var h = FakeBbvaResource.lastHeaders;
    assertEquals("Bearer test-token", h.getHeaderString("Authorization"));
    assertEquals("client_credentials", FakeBbvaOAuthResource.lastGrantType);
    assertEquals("test-client", FakeBbvaOAuthResource.lastClientId);
    assertSigned("post", "/remittance", h);
  }

  @Test
  void patchIsSignedWithItsMethodAndPath() throws Exception {
    given().contentType(ContentType.JSON).body(PATCH)
        .when().patch(BASE + "/remittances/" + FakeBbvaResource.ID).then().statusCode(204);
    assertSigned("patch", "/remittances/" + FakeBbvaResource.ID, FakeBbvaResource.lastHeaders);
  }

  /** Reconstruye lo que firma el pre-request de Postman y lo verifica con la clave pública de test. */
  private static void assertSigned(String method, String relativePath, HttpHeaders h) throws Exception {
    String raw = FakeBbvaResource.lastRawBody;
    // El cuerpo viaja minificado y el Digest es su SHA-512.
    assertEquals(MAPPER.writeValueAsString(MAPPER.readTree(raw)), raw);
    String digest = "SHA-512=" + Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-512").digest(raw.getBytes(StandardCharsets.UTF_8)));
    assertEquals(digest, h.getHeaderString("Digest"));

    String date = h.getHeaderString("Date");
    assertTrue(date.matches("[A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT"), date);

    URI url = URI.create(ConfigProvider.getConfig().getValue("quarkus.rest-client.bbva-bulk-payments.url", String.class));
    assertEquals(url.getRawAuthority(), h.getHeaderString("Host"));

    String signatureHeader = h.getHeaderString("Signature");
    String prefix = "algorithm=\"rsa-sha512\",headers=\"(request-target) host date digest\",signature=\"";
    assertTrue(signatureHeader.startsWith(prefix) && signatureHeader.endsWith("\""), signatureHeader);
    byte[] signature = Base64.getDecoder().decode(
        signatureHeader.substring(prefix.length(), signatureHeader.length() - 1));

    String signingString = "(request-target):" + method + " " + url.getRawPath() + relativePath + "\n"
        + "host:" + url.getRawAuthority() + "\n"
        + "date:" + date + "\n"
        + "digest:" + digest;
    Signature verifier = Signature.getInstance("SHA512withRSA");
    verifier.initVerify(BbvaTestKeyResource.publicKey());
    verifier.update(signingString.getBytes(StandardCharsets.UTF_8));
    assertTrue(verifier.verify(signature), "la firma no corresponde a la cadena esperada");
  }

  @Test
  void tokenIsCachedBetweenCalls() {
    given().contentType(ContentType.JSON).body(CREATE).when().post(BASE + "/remittance").then().statusCode(201);
    int calls = FakeBbvaOAuthResource.CALLS.get();
    given().contentType(ContentType.JSON).body(CREATE).when().post(BASE + "/remittance").then().statusCode(201);
    assertEquals(calls, FakeBbvaOAuthResource.CALLS.get());
  }

  @Test
  void upstream401IsRelayedAndInvalidatesToken() {
    given().contentType(ContentType.JSON).body(CREATE.replace("Agregar varios pagos soles", "UNAUTHORIZED"))
        .when().post(BASE + "/remittance")
        .then().statusCode(401).body("messages[0].code", equalTo("invalidToken"));
    int calls = FakeBbvaOAuthResource.CALLS.get();
    given().contentType(ContentType.JSON).body(CREATE).when().post(BASE + "/remittance").then().statusCode(201);
    assertEquals(calls + 1, FakeBbvaOAuthResource.CALLS.get());
  }

  @Test
  void patchIsForwardedAndReturns204() {
    given().contentType(ContentType.JSON).body(PATCH)
        .when().patch(BASE + "/remittances/" + FakeBbvaResource.ID)
        .then().statusCode(204);
    assertEquals("/remittances/" + FakeBbvaResource.ID, FakeBbvaResource.lastPath);
    assertEquals("CLOSED", FakeBbvaResource.lastBody.path("status").asText());
  }

  @Test
  void closingSettlementIsForwardedAndReturns204() {
    given().contentType(ContentType.JSON).body(CLOSE)
        .when().post(BASE + "/remittances/" + FakeBbvaResource.ID + "/closing-settlement")
        .then().statusCode(204);
    assertEquals("/remittances/" + FakeBbvaResource.ID + "/closing-settlement", FakeBbvaResource.lastPath);
  }

  @Test
  void upstreamErrorIsRelayedAsIs() {
    given().contentType(ContentType.JSON).body(CREATE.replace("Agregar varios pagos soles", "FAIL"))
        .when().post(BASE + "/remittance")
        .then()
        .statusCode(400)
        .body("messages[0].code", equalTo("totalRegistersNotMatch"))
        .body("messages[0].type", equalTo("ERROR"));
  }

  @Test
  void slowUpstreamReturns504() {
    given().contentType(ContentType.JSON).body(CREATE.replace("Agregar varios pagos soles", "SLOW"))
        .when().post(BASE + "/remittance")
        .then()
        .statusCode(504)
        .body("messages[0].code", equalTo("upstreamTimeout"))
        .body("messages[0].type", equalTo("ERROR"));
  }

  @Test
  void malformedJsonIsRejectedLocally() {
    given().contentType(ContentType.JSON).body("{not json").when().post(BASE + "/remittance")
        .then().statusCode(400).body("messages[0].type", equalTo("ERROR"));
  }
}

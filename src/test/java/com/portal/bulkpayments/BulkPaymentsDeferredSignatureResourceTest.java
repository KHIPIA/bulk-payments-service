package com.portal.bulkpayments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BulkPaymentsDeferredSignatureResourceTest extends RemittanceApiTestBase {

  String basePath() { return "/api/bulk-payments-deferred-signature"; }
  String locationPrefix() { return "/pe/bulk-payments-deferred-signature/v1/remittances/"; }
  String functionalErrorMessage() { return "Ocurrió un error al enviar la remesa al módulo de pendiente de firma."; }
  String expectedName(String sent) { return "OB-" + sent; }

  @Test
  void obPrefixNotDuplicated() {
    post(validBody("OB-Planilla", DEBTOR_ACCOUNT, List.of(internal(10)), 1, 10))
        .then().statusCode(201).body("data.name", equalTo("OB-Planilla"));
  }

  @Test
  void debtorIdentityIsOptional() {
    Map<String, Object> body = new HashMap<>(validBody("P", DEBTOR_ACCOUNT, List.of(internal(10)), 1, 10));
    body.put("remittances", List.of(Map.of(
        "instructionId", "INS-1",
        "debtorAccount", Map.of("number", DEBTOR_ACCOUNT),
        "payments", List.of(internal(10)))));
    post(body).then().statusCode(201);
  }

  @Test
  void debtorIdentitySubfieldsRequiredWhenPresent() {
    Map<String, Object> body = new HashMap<>(validBody("P", DEBTOR_ACCOUNT, List.of(internal(10)), 1, 10));
    body.put("remittances", List.of(Map.of(
        "instructionId", "INS-1",
        "debtor", Map.of("identity", Map.of("id", "1")),
        "debtorAccount", Map.of("number", DEBTOR_ACCOUNT),
        "payments", List.of(internal(10)))));
    post(body).then().statusCode(400).body("messages[0].message", equalTo(
        "Mandatory parameters not reported: remittances[0].debtor.identity.documentType"));
  }

  @Test
  void patchDoesNotRequireName() {
    String id = createRemittance();
    Map<String, Object> body = new HashMap<>(validBody("x", DEBTOR_ACCOUNT, List.of(internal(25)), 1, 25));
    body.remove("name");
    given().contentType(ContentType.JSON).body(body).when().patch(basePath() + "/remittances/" + id)
        .then().statusCode(204);
  }
}

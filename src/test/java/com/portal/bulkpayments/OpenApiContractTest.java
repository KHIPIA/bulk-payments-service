package com.portal.bulkpayments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** El contrato de bulk-payments sale de META-INF/openapi.yaml; deferred-signature sigue generándose del código. */
@QuarkusTest
class OpenApiContractTest {

  @Test
  void bulkPaymentsComesFromYamlContract() {
    given().queryParam("format", "json").when().get("/openapi")
        .then()
        .statusCode(200)
        .body("paths.'/api/bulk-payments/remittance'.post.operationId", equalTo("createRemittance"))
        .body("paths.'/api/bulk-payments/remittances/{id}'.patch.operationId", equalTo("addPayments"))
        .body("paths.'/api/bulk-payments/remittances/{id}/closing-settlement'.post.operationId", equalTo("closingSettlement"))
        .body("components.schemas.BbvaRemittance.properties", hasKey("debtorName"))
        .body("components.schemas.BbvaPayment.properties", hasKey("endToEndId"))
        .body("components.schemas.BbvaAccount.properties", hasKey("numberType"))
        .body("paths.'/api/bulk-payments/remittance'.post.responses.keySet()", hasItems("201", "400", "401", "502", "504"));
  }

  @Test
  void deferredSignatureIsStillDocumented() {
    given().queryParam("format", "json").when().get("/openapi")
        .then()
        .statusCode(200)
        .body("paths.'/api/bulk-payments-deferred-signature/remittance'.post", notNullValue());
  }

  @Test
  void noGenericJsonNodeSchemaLeaks() {
    given().queryParam("format", "json").when().get("/openapi")
        .then()
        .statusCode(200)
        .body(not(containsString("JsonNode")));
  }
}

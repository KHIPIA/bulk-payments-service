package com.portal.bulkpayments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** BBVA inalcanzable (puerto cerrado): el proxy responde 502 con el envelope común. */
@QuarkusTest
@QuarkusTestResource(BbvaTestKeyResource.class)
@TestProfile(BulkPaymentsUnreachableTest.UnreachableBbvaProfile.class)
class BulkPaymentsUnreachableTest {

  public static class UnreachableBbvaProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.rest-client.bbva-bulk-payments.url", "http://localhost:1/pe/bulk-payments/v1");
    }
  }

  @Test
  void unreachableUpstreamReturns502() {
    given().contentType(ContentType.JSON).body(BulkPaymentsResourceTest.CREATE)
        .when().post(BulkPaymentsResourceTest.BASE + "/remittance")
        .then()
        .statusCode(502)
        .body("messages[0].code", equalTo("upstreamUnavailable"))
        .body("messages[0].type", equalTo("ERROR"));
  }
}

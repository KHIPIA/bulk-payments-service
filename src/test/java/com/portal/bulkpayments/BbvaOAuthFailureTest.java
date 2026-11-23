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

/** BBVA rechaza las credenciales OAuth: el proxy responde 502 upstreamAuthFailed sin llamar a la API de remesas. */
@QuarkusTest
@QuarkusTestResource(BbvaTestKeyResource.class)
@TestProfile(BbvaOAuthFailureTest.WrongSecretProfile.class)
class BbvaOAuthFailureTest {

  public static class WrongSecretProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("bbva.auth.client-secret", "wrong");
    }
  }

  @Test
  void rejectedCredentialsReturn502() {
    FakeBbvaResource.lastPath = null;
    given().contentType(ContentType.JSON).body(BulkPaymentsResourceTest.CREATE)
        .when().post(BulkPaymentsResourceTest.BASE + "/remittance")
        .then()
        .statusCode(502)
        .body("messages[0].code", equalTo("upstreamAuthFailed"))
        .body("messages[0].type", equalTo("ERROR"));
    org.junit.jupiter.api.Assertions.assertNull(FakeBbvaResource.lastPath);
  }
}

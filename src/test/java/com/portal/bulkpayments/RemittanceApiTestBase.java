package com.portal.bulkpayments;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Casos del mock local de remesas. Hoy solo lo usa deferred-signature: /api/bulk-payments es un
 * proxy a BBVA y se prueba en BulkPaymentsResourceTest.
 */
abstract class RemittanceApiTestBase {

  static final String DEBTOR_ACCOUNT = "00110123456789012345";

  abstract String basePath();

  abstract String locationPrefix();

  abstract String functionalErrorMessage();

  abstract String expectedName(String sent);

  static Map<String, Object> payment(String type, String account, double amount) {
    Map<String, Object> creditor =
        "INTERBANK_PAYMENT".equals(type)
            ? Map.of("name", "Maria Perez", "identity", Map.of("id", "12345678", "documentType", "DNI"))
            : Map.of("name", "Maria Perez");
    return Map.of(
        "paymentType", type,
        "paymentAmount", Map.of("currency", "PEN", "amount", amount),
        "creditor", creditor,
        "creditorAccount", Map.of("number", account));
  }

  static Map<String, Object> internal(double amount) {
    return payment("INTERNAL_PAYMENT", "001101234567890123", amount);
  }

  static Map<String, Object> validBody(String name, String debtorAccount, List<Map<String, Object>> payments, int count, double total) {
    return Map.of(
        "name", name,
        "status", "DRAFT",
        "header", Map.of("numberOfTransactions", count, "controlSum", List.of(Map.of("currency", "PEN", "amount", total))),
        "remittances",
            List.of(
                Map.of(
                    "instructionId", "INS-1",
                    "debtor", Map.of("identity", Map.of("id", "20100070970", "documentType", "RUC")),
                    "debtorAccount", Map.of("number", debtorAccount),
                    "payments", payments)));
  }

  Map<String, Object> defaultBody() {
    return validBody("Planilla", DEBTOR_ACCOUNT, List.of(internal(100.5), internal(50)), 2, 150.5);
  }

  Response post(Object body) {
    return given().contentType(ContentType.JSON).body(body).when().post(basePath() + "/remittance");
  }

  String createRemittance() {
    return post(defaultBody()).then().statusCode(201).extract().path("data.id");
  }

  Response close(String id, String account) {
    Map<String, Object> body = Map.of("remittances", List.of(Map.of("debtorAccount", Map.of("number", account))));
    return given().contentType(ContentType.JSON).body(body).when().post(basePath() + "/remittances/" + id + "/closing-settlement");
  }

  void assertError(Response r, int status, String code) {
    r.then().statusCode(status).body("messages[0].code", equalTo(code)).body("messages[0].type", equalTo("ERROR"));
  }

  @Test
  void createReturns201WithLocationAndData() {
    Response r = post(defaultBody());
    r.then()
        .statusCode(201)
        .header("Location", startsWith(locationPrefix()))
        .body("data.id", hasLength(32))
        .body("data.name", equalTo(expectedName("Planilla")))
        .body("data.status", equalTo("DRAFT"))
        .body("data.header.numberOfTransactions", equalTo(2))
        .body("data.remittances[0].payments.size()", equalTo(2));
    String id = r.path("data.id");
    org.junit.jupiter.api.Assertions.assertEquals(locationPrefix() + id, r.header("Location"));
  }

  @Test
  void emptyBodyListsMissingFields() {
    given().contentType(ContentType.JSON).body("{}").when().post(basePath() + "/remittance")
        .then().statusCode(400)
        .body("messages[0].code", equalTo("mandatoryParametersMissing"))
        .body("messages[0].message", equalTo(
            "Mandatory parameters not reported: name, status, header.numberOfTransactions, header.controlSum, remittances"));
  }

  @Test
  void interbankRequiresCreditorIdentity() {
    Map<String, Object> p = Map.of(
        "paymentType", "INTERBANK_PAYMENT",
        "paymentAmount", Map.of("currency", "PEN", "amount", 10),
        "creditor", Map.of("name", "Ana"),
        "creditorAccount", Map.of("number", "00110123456789012345"));
    post(validBody("X", DEBTOR_ACCOUNT, List.of(p), 1, 10)).then()
        .statusCode(400)
        .body("messages[0].message", equalTo(
            "Mandatory parameters not reported: remittances[0].payments[0].creditor.identity.id, remittances[0].payments[0].creditor.identity.documentType"));
  }

  @Test
  void mixedPaymentTypesRejected() {
    var body = validBody("X", DEBTOR_ACCOUNT,
        List.of(internal(10), payment("INTERBANK_PAYMENT", "00110123456789012345", 10)), 2, 20);
    assertError(post(body), 400, "invalidParameters");
  }

  @Test
  void wrongCreditorAccountLengthRejected() {
    var body = validBody("X", DEBTOR_ACCOUNT, List.of(payment("INTERNAL_PAYMENT", "123", 10)), 1, 10);
    assertError(post(body), 400, "invalidParameters");
  }

  @Test
  void transactionCountMismatchRejected() {
    assertError(post(validBody("X", DEBTOR_ACCOUNT, List.of(internal(10)), 2, 10)), 400, "totalRegistersNotMatch");
  }

  @Test
  void controlSumMismatchRejected() {
    assertError(post(validBody("X", DEBTOR_ACCOUNT, List.of(internal(10)), 1, 11)), 400, "totalAmountNotMatch");
  }

  @Test
  void malformedJsonReturnsEnvelope() {
    given().contentType(ContentType.JSON).body("{not json").when().post(basePath() + "/remittance")
        .then().statusCode(400).body("messages[0].type", equalTo("ERROR"));
  }

  @Test
  void patchUnknownIdIsSenderNotFound() {
    assertError(
        given().contentType(ContentType.JSON).body(defaultBody()).when().patch(basePath() + "/remittances/NOPE"),
        400, "senderNotFound");
  }

  @Test
  void patchAppendsPayments() {
    String id = createRemittance();
    var body = validBody("Planilla", DEBTOR_ACCOUNT, List.of(internal(25)), 1, 25);
    given().contentType(ContentType.JSON).body(body).when().patch(basePath() + "/remittances/" + id)
        .then().statusCode(204);
  }

  @Test
  void patchValidatesBody() {
    String id = createRemittance();
    assertError(
        given().contentType(ContentType.JSON).body(validBody("P", DEBTOR_ACCOUNT, List.of(internal(25)), 3, 25))
            .when().patch(basePath() + "/remittances/" + id),
        400, "totalRegistersNotMatch");
  }

  @Test
  void closingSettlementUnknownIdIsSenderNotFound() {
    assertError(close("NOPE", DEBTOR_ACCOUNT), 400, "senderNotFound");
  }

  @Test
  void closingSettlementRequiresAccount() {
    String id = createRemittance();
    given().contentType(ContentType.JSON).body("{}").when().post(basePath() + "/remittances/" + id + "/closing-settlement")
        .then().statusCode(400)
        .body("messages[0].code", equalTo("mandatoryParametersMissing"))
        .body("messages[0].message", equalTo("Mandatory parameters not reported: remittances[0].debtorAccount.number"));
  }

  @Test
  void closingSettlementAccountMismatch() {
    assertError(close(createRemittance(), "99999999999999999999"), 400, "notMatchingAccount");
  }

  @Test
  void closingSettlementFunctionalErrorForAccountEndingIn0000() {
    String account = "00110123456780000000".substring(0, 16) + "0000";
    var body = validBody("Z", account, List.of(internal(10)), 1, 10);
    String id = post(body).then().statusCode(201).extract().path("data.id");
    close(id, account).then()
        .statusCode(400)
        .body("messages[0].code", equalTo("functionalError"))
        .body("messages[0].message", equalTo(functionalErrorMessage()));
  }

  @Test
  void closingSettlementSucceedsOnceThenIsFull() {
    String id = createRemittance();
    close(id, DEBTOR_ACCOUNT).then().statusCode(204);
    assertError(close(id, DEBTOR_ACCOUNT), 400, "fullPaymentRemittance");
    assertError(
        given().contentType(ContentType.JSON).body(defaultBody()).when().patch(basePath() + "/remittances/" + id),
        400, "fullPaymentRemittance");
  }

}

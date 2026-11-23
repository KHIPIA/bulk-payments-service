package com.portal.bulkpayments.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.portal.bulkpayments.exception.ApiException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** Detalles del signer que deben igualar al pre-request de Postman. */
class BbvaRequestSignerTest {

  @Test
  void dateMatchesJavascriptToUTCStringWithTwoDigitDay() {
    ZonedDateTime date = ZonedDateTime.of(2026, 11, 1, 9, 5, 3, 0, ZoneOffset.UTC);
    // new Date(Date.UTC(2026, 10, 1, 9, 5, 3)).toUTCString()
    assertEquals("Sun, 01 Nov 2026 09:05:03 GMT", BbvaRequestSigner.DATE_FORMAT.format(date));
  }

  @Test
  void invalidKeyIsUpstreamAuthFailed() {
    ApiException e = assertThrows(ApiException.class, () -> BbvaRequestSigner.parse("bm8gZXMgdW5hIGNsYXZl"));
    assertEquals(502, e.status());
    assertEquals("upstreamAuthFailed", e.code());
  }
}

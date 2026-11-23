package com.portal.bulkpayments.dto;

import jakarta.ws.rs.core.MediaType;

/**
 * Respuesta de BBVA ya leída y desacoplada del REST client. location, body y mediaType pueden ser
 * null (por ejemplo, en un 204).
 */
public record UpstreamResponse(int status, String location, String body, MediaType mediaType) {

  public boolean hasBody() {
    return body != null && !body.isEmpty();
  }
}

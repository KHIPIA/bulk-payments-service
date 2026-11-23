package com.portal.bulkpayments.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.portal.bulkpayments.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ApiExceptionMapper {

  @ServerExceptionMapper
  public Response mapApiException(ApiException e) {
    return build(e.status(), e.code(), e.getMessage());
  }

  /** JSON mal formado o con tipos incorrectos: mismo envelope, 400. */
  @ServerExceptionMapper
  public Response mapJsonException(JsonProcessingException e) {
    return build(400, "invalidParameters", "Invalid Parameters: malformed request body.");
  }

  /** Errores de framework (cuerpo ilegible, ruta o método inexistente, etc.): mismo envelope. */
  @ServerExceptionMapper
  public Response mapWebApplicationException(WebApplicationException e) {
    int status = e.getResponse().getStatus();
    String code = status == 400 ? "invalidParameters" : "httpError";
    return build(status, code, status == 400 ? "Invalid Parameters: malformed request body." : String.valueOf(e.getMessage()));
  }

  private static Response build(int status, String code, String message) {
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON)
        .entity(ErrorResponse.of(code, message))
        .build();
  }
}

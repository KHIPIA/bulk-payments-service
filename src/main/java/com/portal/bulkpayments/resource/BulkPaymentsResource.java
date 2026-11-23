package com.portal.bulkpayments.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.bulkpayments.dto.UpstreamResponse;
import com.portal.bulkpayments.service.BbvaBulkPaymentsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * Pagos masivos: proxy al sandbox BBVA (/pe/bulk-payments/v1) vía {@link BbvaBulkPaymentsService}.
 * Contrato en META-INF/openapi.yaml (excluido del escaneo de anotaciones); mantenerlo al cambiar endpoints.
 */
@Path("/bulk-payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class BulkPaymentsResource {

  @Inject BbvaBulkPaymentsService service;

  @POST
  @Path("/remittance")
  public Response create(JsonNode body) {
    log.info("test");
    return toResponse(service.create(body));
  }

  @PATCH
  @Path("/remittances/{id}")
  public Response addPayments(@PathParam("id") String id, JsonNode body) {
    return toResponse(service.addPayments(id, body));
  }

  @POST
  @Path("/remittances/{id}/closing-settlement")
  public Response closingSettlement(@PathParam("id") String id, JsonNode body) {
    return toResponse(service.closingSettlement(id, body));
  }

  private static Response toResponse(UpstreamResponse upstream) {
    Response.ResponseBuilder out = Response.status(upstream.status());
    if (upstream.location() != null) {
      out.header(HttpHeaders.LOCATION, upstream.location());
    }
    if (upstream.hasBody()) {
      out.entity(upstream.body())
          .type(upstream.mediaType() != null ? upstream.mediaType() : MediaType.APPLICATION_JSON_TYPE);
    }
    return out.build();
  }
}

package com.portal.bulkpayments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Doble del sandbox BBVA, solo en tests: registra la última petición y responde como la API real. */
@Path("/fake-bbva/pe/bulk-payments/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FakeBbvaResource {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static final String ID = "298C13EF76A846218677DB7931E6EB00";

  static volatile HttpHeaders lastHeaders;
  static volatile JsonNode lastBody;
  /** Cuerpo exacto recibido, para comprobar el Digest. */
  static volatile String lastRawBody;
  static volatile String lastPath;

  @POST
  @Path("/remittance")
  public Response create(String raw, @Context HttpHeaders headers) {
    JsonNode body = record("/remittance", raw, headers);
    if ("SLOW".equals(body.path("name").asText())) {
      // Supera el read-timeout de %test (1 s) para provocar el 504.
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if ("UNAUTHORIZED".equals(body.path("name").asText())) {
      return Response.status(401)
          .entity("{\"messages\":[{\"code\":\"invalidToken\",\"message\":\"Token expired\",\"type\":\"ERROR\"}]}")
          .build();
    }
    if ("FAIL".equals(body.path("name").asText())) {
      return Response.status(400)
          .entity("{\"messages\":[{\"code\":\"totalRegistersNotMatch\",\"message\":\"There is a difference\",\"type\":\"ERROR\"}]}")
          .build();
    }
    return Response.status(201)
        .header("Location", "/pe/bulk-payments/v1/remittances/" + ID) // como el sandbox real: sin cuerpo
        .build();
  }

  @PATCH
  @Path("/remittances/{id}")
  public Response addPayments(@PathParam("id") String id, String raw, @Context HttpHeaders headers) {
    record("/remittances/" + id, raw, headers);
    return Response.noContent().build();
  }

  @POST
  @Path("/remittances/{id}/closing-settlement")
  public Response close(@PathParam("id") String id, String raw, @Context HttpHeaders headers) {
    record("/remittances/" + id + "/closing-settlement", raw, headers);
    return Response.noContent().build();
  }

  private static JsonNode record(String path, String raw, HttpHeaders headers) {
    JsonNode body;
    try {
      body = MAPPER.readTree(raw);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
    lastPath = path;
    lastRawBody = raw;
    lastBody = body;
    lastHeaders = headers;
    return body;
  }
}

package com.portal.bulkpayments;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.atomic.AtomicInteger;

/** Doble del endpoint OAuth de BBVA, solo en tests: cuenta las peticiones de token. */
@Path("/fake-bbva/auth/oauth/v2")
public class FakeBbvaOAuthResource {

  static final AtomicInteger CALLS = new AtomicInteger();
  static volatile String lastGrantType;
  static volatile String lastClientId;

  @POST
  @Path("/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public Response token(
      @FormParam("grant_type") String grantType,
      @FormParam("client_id") String clientId,
      @FormParam("client_secret") String clientSecret) {
    CALLS.incrementAndGet();
    lastGrantType = grantType;
    lastClientId = clientId;
    if (!"test-secret".equals(clientSecret)) {
      return Response.status(401)
          .entity("{\"error\":\"invalid_client\",\"error_description\":\"The given client credentials were not valid\"}")
          .build();
    }
    return Response.ok(
            "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":1800,"
                + "\"scope\":\"oob\",\"resource\":[\"https://sbx.pe.bbvaapimarket.com/*\"]}")
        .build();
  }
}

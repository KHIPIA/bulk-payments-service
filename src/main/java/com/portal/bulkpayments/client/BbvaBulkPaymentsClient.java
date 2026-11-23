package com.portal.bulkpayments.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * API real de pagos masivos (sandbox BBVA). El cuerpo viaja como String ya minificado: es exactamente
 * lo que se firmó (el Digest debe coincidir byte a byte). Authorization lo pone el servicio con el token
 * de BbvaTokenProvider; Digest, Signature, Date y Host salen de BbvaRequestSigner.
 */
@RegisterRestClient(configKey = "bbva-bulk-payments")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BbvaBulkPaymentsClient {

  @POST
  @Path("/remittance")
  Response create(
      @HeaderParam("Authorization") String authorization,
      @HeaderParam("Digest") String digest,
      @HeaderParam("Signature") String signature,
      @HeaderParam("Date") String date,
      @HeaderParam("Host") String host,
      String body);

  @PATCH
  @Path("/remittances/{id}")
  Response addPayments(
      @HeaderParam("Authorization") String authorization,
      @HeaderParam("Digest") String digest,
      @HeaderParam("Signature") String signature,
      @HeaderParam("Date") String date,
      @HeaderParam("Host") String host,
      @PathParam("id") String id,
      String body);

  @POST
  @Path("/remittances/{id}/closing-settlement")
  Response closingSettlement(
      @HeaderParam("Authorization") String authorization,
      @HeaderParam("Digest") String digest,
      @HeaderParam("Signature") String signature,
      @HeaderParam("Date") String date,
      @HeaderParam("Host") String host,
      @PathParam("id") String id,
      String body);
}

package com.portal.bulkpayments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.bulkpayments.client.BbvaBulkPaymentsClient;
import com.portal.bulkpayments.client.BbvaRequestSigner;
import com.portal.bulkpayments.client.BbvaRequestSigner.SignedHeaders;
import com.portal.bulkpayments.dto.UpstreamResponse;
import com.portal.bulkpayments.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.function.Function;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Llama a la API de pagos masivos de BBVA y devuelve su respuesta tal cual (status, Location, cuerpo).
 * No valida (BBVA es la fuente de verdad) ni reintenta (el POST no es idempotente).
 * Antes de cada llamada obtiene el token OAuth y firma la petición (Digest, Signature, Date, Host).
 * Errores propios: 502 upstreamUnavailable si no hay conexión, 504 upstreamTimeout si BBVA no responde,
 * 502 upstreamAuthFailed si no se obtiene el token o no se puede firmar. Un 401 de BBVA descarta el token cacheado.
 */
@ApplicationScoped
public class BbvaBulkPaymentsService {

  private static final Logger LOG = Logger.getLogger(BbvaBulkPaymentsService.class);

  @Inject @RestClient BbvaBulkPaymentsClient client;
  @Inject BbvaTokenProvider tokens;
  @Inject BbvaRequestSigner signer;
  @Inject ObjectMapper mapper;

  /** POST /remittance: crea la remesa en BBVA. */
  public UpstreamResponse create(JsonNode body) {
    return call("create", "post", "/remittance", body, r ->
        client.create(r.authorization(), r.h().digest(), r.h().signature(), r.h().date(), r.h().host(), r.body()));
  }

  /** PATCH /remittances/{id}: agrega pagos a una remesa existente. */
  public UpstreamResponse addPayments(String id, JsonNode body) {
    return call("addPayments", "patch", "/remittances/" + id, body, r ->
        client.addPayments(r.authorization(), r.h().digest(), r.h().signature(), r.h().date(), r.h().host(), id, r.body()));
  }

  /** POST /remittances/{id}/closing-settlement: liquida la remesa. */
  public UpstreamResponse closingSettlement(String id, JsonNode body) {
    return call("closingSettlement", "post", "/remittances/" + id + "/closing-settlement", body, r ->
        client.closingSettlement(r.authorization(), r.h().digest(), r.h().signature(), r.h().date(), r.h().host(), id, r.body()));
  }

  /** Lo que necesita cada llamada al client: token, cabeceras firmadas y el cuerpo exacto que se firmó. */
  private record SignedRequest(String authorization, SignedHeaders h, String body) {}

  private UpstreamResponse call(
      String operation, String method, String path, JsonNode body, Function<SignedRequest, Response> invoke) {
    // Fuera del try: los errores de token y firma ya son ApiException.
    String authorization = tokens.authorization();
    String json = serialize(body);
    SignedRequest request = new SignedRequest(authorization, signer.sign(method, path, json), json);
    try (Response response = invoke.apply(request)) { // try-with-resources: cierra la conexión con BBVA
      return read(operation, response);
    } catch (ProcessingException e) {
      throw UpstreamErrors.connectionError(operation, e);
    }
  }

  /** JSON minificado, como JSON.stringify(JSON.parse(raw)) del pre-request de Postman. */
  private String serialize(JsonNode body) {
    if (body == null || body.isNull() || body.isMissingNode()) {
      return "";
    }
    try {
      return mapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("invalidBody", "No se pudo serializar el cuerpo.");
    }
  }

  /** Copia lo que respondió BBVA. Solo se registra el status: cabeceras y cuerpo llevan datos sensibles. */
  private UpstreamResponse read(String operation, Response response) {
    LOG.infof("BBVA %s respondió %d", operation, response.getStatus());
    if (response.getStatus() == 401) {
      tokens.invalidate(); // sin reintento (POST no idempotente): la próxima llamada pedirá otro token
    }
    String body = response.hasEntity() ? response.readEntity(String.class) : null;
    return new UpstreamResponse(
        response.getStatus(),
        response.getHeaderString(HttpHeaders.LOCATION),
        body,
        response.getMediaType());
  }
}

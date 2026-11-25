package com.portal.bulkpayments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.bulkpayments.client.BbvaBulkPaymentsClient;
import com.portal.bulkpayments.client.BbvaRequestSigner;
import com.portal.bulkpayments.client.BbvaRequestSigner.SignedHeaders;
import com.portal.bulkpayments.dto.ProxySuccessResponse;
import com.portal.bulkpayments.dto.UpstreamResponse;
import com.portal.bulkpayments.exception.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.function.Function;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Llama a la API de pagos masivos de BBVA y devuelve su respuesta (status, Location, cuerpo). Si BBVA responde
 * 2xx sin cuerpo, el proxy agrega {httpCode, message, id}; un 204 pasa a 200 porque un 204 no lleva cuerpo.
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
    return call("create", "Remesa creada correctamente.", "post", "/remittance", body, r ->
        client.create(r.authorization(), r.h().digest(), r.h().signature(), r.h().date(), r.h().host(), r.body()));
  }

  /** PATCH /remittances/{id}: agrega pagos a una remesa existente. */
  public UpstreamResponse addPayments(String id, JsonNode body) {
    return call("addPayments", "Pagos agregados correctamente.", "patch", "/remittances/" + id, body, r ->
        client.addPayments(r.authorization(), r.h().digest(), r.h().signature(), r.h().date(), r.h().host(), id, r.body()));
  }

  /** POST /remittances/{id}/closing-settlement: liquida la remesa. */
  public UpstreamResponse closingSettlement(String id, JsonNode body) {
    return call("closingSettlement", "Remesa liquidada correctamente.", "post", "/remittances/" + id + "/closing-settlement", body, r ->
        client.closingSettlement(r.authorization(), r.h().digest(), r.h().signature(), r.h().date(), r.h().host(), id, r.body()));
  }

  /** Lo que necesita cada llamada al client: token, cabeceras firmadas y el cuerpo exacto que se firmó. */
  private record SignedRequest(String authorization, SignedHeaders h, String body) {}

  private UpstreamResponse call(
      String operation, String successMessage, String method, String path, JsonNode body, Function<SignedRequest, Response> invoke) {
    // Fuera del try: los errores de token y firma ya son ApiException.
    String authorization = tokens.authorization();
    String json = serialize(body);
    SignedRequest request = new SignedRequest(authorization, signer.sign(method, path, json), json);
    try (Response response = invoke.apply(request)) { // try-with-resources: cierra la conexión con BBVA
      return read(operation, successMessage, response);
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
  private UpstreamResponse read(String operation, String successMessage, Response response) {
    LOG.infof("BBVA %s respondió %d", operation, response.getStatus());
    if (response.getStatus() == 401) {
      tokens.invalidate(); // sin reintento (POST no idempotente): la próxima llamada pedirá otro token
    }
    String body = response.hasEntity() ? response.readEntity(String.class) : null;
    String location = response.getHeaderString(HttpHeaders.LOCATION);
    int status = response.getStatus();
    if (status / 100 == 2 && (body == null || body.isEmpty())) {
      // BBVA solo responde el status (y Location al crear): se informa el resultado al cliente.
      int outStatus = status == 204 ? 200 : status;
      return new UpstreamResponse(
          outStatus, location, successBody(outStatus, successMessage, location), MediaType.APPLICATION_JSON_TYPE);
    }
    return new UpstreamResponse(status, location, body, response.getMediaType());
  }

  private String successBody(int status, String message, String location) {
    String id = location == null || location.isBlank() ? null : location.substring(location.lastIndexOf('/') + 1);
    try {
      return mapper.writeValueAsString(new ProxySuccessResponse(status, message, id));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}

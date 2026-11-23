package com.portal.bulkpayments.resource;

import com.portal.bulkpayments.domain.ApiVariant;
import com.portal.bulkpayments.dto.RemittanceRequest;
import com.portal.bulkpayments.dto.RemittanceResponse;
import com.portal.bulkpayments.service.RemittanceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** Pagos masivos que se envían a pendiente de firma (nombre con prefijo OB-). */
@Path("/api/bulk-payments-deferred-signature")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "bulk-payments-deferred-signature")
public class BulkPaymentsDeferredSignatureResource {

  private static final ApiVariant VARIANT = ApiVariant.DEFERRED;

  @Inject RemittanceService service;

  @POST
  @Path("/remittance")
  public Response create(RemittanceRequest body) {
    var data = service.create(VARIANT, body);
    return Response.status(Response.Status.CREATED)
        .header("Location", VARIANT.locationFor(data.id()))
        .entity(new RemittanceResponse(data))
        .build();
  }

  @PATCH
  @Path("/remittances/{id}")
  public Response addPayments(@PathParam("id") String id, RemittanceRequest body) {
    service.addPayments(VARIANT, id, body);
    return Response.noContent().build();
  }

  @POST
  @Path("/remittances/{id}/closing-settlement")
  public Response closingSettlement(@PathParam("id") String id, RemittanceRequest body) {
    service.closeSettlement(VARIANT, id, body);
    return Response.noContent().build();
  }
}

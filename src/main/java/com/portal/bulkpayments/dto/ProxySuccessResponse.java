package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo que devuelve el proxy cuando BBVA responde 2xx sin cuerpo: código HTTP, mensaje de éxito y,
 * si BBVA envió Location, el id de la remesa (último segmento).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProxySuccessResponse(int httpCode, String message, String id) {}

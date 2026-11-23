package com.portal.bulkpayments.service;

import com.portal.bulkpayments.exception.ApiException;
import jakarta.ws.rs.ProcessingException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.jboss.logging.Logger;

/** Traduce fallos de conexión con BBVA: 504 si se agotó el tiempo de espera, 502 si no se pudo conectar. */
final class UpstreamErrors {

  private static final Logger LOG = Logger.getLogger(UpstreamErrors.class);

  private UpstreamErrors() {}

  static ApiException connectionError(String operation, ProcessingException e) {
    if (isTimeout(e)) {
      LOG.warnf("BBVA %s sin respuesta (timeout)", operation);
      return new ApiException(504, "upstreamTimeout", "BBVA no respondió a tiempo.");
    }
    LOG.warnf("BBVA %s no disponible: %s", operation, e.getMessage());
    return new ApiException(502, "upstreamUnavailable", "No se pudo conectar con BBVA.");
  }

  /** El REST client envuelve el timeout real (de Vert.x o de socket) dentro de la ProcessingException. */
  private static boolean isTimeout(Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }
}

package com.portal.bulkpayments.domain;

/**
 * Las dos APIs comparten lógica y solo difieren en estos parámetros
 * (ver routes/bulkPayments.js y routes/bulkPaymentsDeferredSignature.js).
 * Hoy solo DEFERRED usa el mock local: /api/bulk-payments es un proxy al sandbox BBVA
 * (BulkPaymentsResource) y BULK se conserva por si se vuelve a simular.
 */
public enum ApiVariant {
  BULK(
      "/pe/bulk-payments/v1/remittances/",
      "Ocurrió un error al procesar la liquidación.",
      null,
      false),
  DEFERRED(
      "/pe/bulk-payments-deferred-signature/v1/remittances/",
      "Ocurrió un error al enviar la remesa al módulo de pendiente de firma.",
      "OB-",
      true);

  private final String locationPrefix;
  private final String functionalErrorMessage;
  private final String namePrefix;
  private final boolean debtorIdentityOptional;

  ApiVariant(String locationPrefix, String functionalErrorMessage, String namePrefix, boolean debtorIdentityOptional) {
    this.locationPrefix = locationPrefix;
    this.functionalErrorMessage = functionalErrorMessage;
    this.namePrefix = namePrefix;
    this.debtorIdentityOptional = debtorIdentityOptional;
  }

  public String locationFor(String id) {
    return locationPrefix + id;
  }

  public String functionalErrorMessage() {
    return functionalErrorMessage;
  }

  /** En deferred-signature debtor.identity es opcional (si viene, sus subcampos son obligatorios). */
  public boolean debtorIdentityOptional() {
    return debtorIdentityOptional;
  }

  /** Deferred-signature exige el prefijo "OB-" para que el firmante identifique la remesa. */
  public String applyNamePrefix(String name) {
    if (namePrefix == null || name.startsWith(namePrefix)) {
      return name;
    }
    return namePrefix + name;
  }

  /** Deferred-signature no exige "name" en PATCH; bulk-payments sí. */
  public boolean requireNameOnPatch() {
    return this == BULK;
  }
}

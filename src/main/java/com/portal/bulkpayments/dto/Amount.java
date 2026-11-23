package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Monto con moneda; se usa tanto en paymentAmount como en header.controlSum. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Amount(String currency, BigDecimal amount) {}

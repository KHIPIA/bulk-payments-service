package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Remittance(
    String instructionId,
    Party debtor,
    Account debtorAccount,
    List<Payment> payments) {}

package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemittanceRequest(
    String name,
    String status,
    Header header,
    List<Remittance> remittances) {}

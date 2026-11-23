package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemittanceData(
    String id,
    String name,
    Header header,
    String status,
    List<Remittance> remittances) {}

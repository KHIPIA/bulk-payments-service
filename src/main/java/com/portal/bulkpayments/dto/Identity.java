package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Identity(String id, String documentType) {}

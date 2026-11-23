package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Party(String name, Identity identity) {}

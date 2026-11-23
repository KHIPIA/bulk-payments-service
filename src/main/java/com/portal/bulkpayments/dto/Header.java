package com.portal.bulkpayments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Header(Integer numberOfTransactions, List<Amount> controlSum) {}

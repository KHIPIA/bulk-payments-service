package com.portal.bulkpayments.dto;

import java.util.List;

/** Envelope de error común: {"messages":[{"code","message","type":"ERROR"}]}. */
public record ErrorResponse(List<Message> messages) {

  public record Message(String code, String message, String type) {}

  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse(List.of(new Message(code, message, "ERROR")));
  }
}

package com.portal.bulkpayments.exception;

/** Error de negocio/validación que se traduce al envelope {"messages":[...]} con el status dado. */
public class ApiException extends RuntimeException {

  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(400, code, message);
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }
}

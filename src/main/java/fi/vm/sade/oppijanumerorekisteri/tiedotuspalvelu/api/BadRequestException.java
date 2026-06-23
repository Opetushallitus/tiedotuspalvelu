package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import java.util.List;
import lombok.Getter;

public class BadRequestException extends RuntimeException {
  @Getter private final Object request;
  @Getter private final ErrorResponse response;

  public static BadRequestException validationError(Object request, String field, String error) {
    var validationError = new ErrorResponse.ValidationError(field, error);
    var response = new ErrorResponse("Validation failed", List.of(validationError));
    return new BadRequestException(request, response);
  }

  private BadRequestException(Object request, ErrorResponse response) {
    super(response.reason());
    this.request = request;
    this.response = response;
  }
}

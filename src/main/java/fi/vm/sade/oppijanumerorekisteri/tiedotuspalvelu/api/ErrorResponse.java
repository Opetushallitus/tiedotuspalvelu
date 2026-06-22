package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import java.util.List;

public record ErrorResponse(String reason, List<ValidationError> validationErrors) {

  public record ValidationError(String field, String error) {}
}

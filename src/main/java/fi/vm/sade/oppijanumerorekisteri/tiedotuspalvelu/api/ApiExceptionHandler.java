package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api.ErrorResponse.ValidationError;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice(assignableTypes = ApiController.class)
public class ApiExceptionHandler {

  private final ObjectMapper objectMapper;

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
    var validationErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
            .toList();
    log.info("Rejected request body: {}", toJson(ex.getBindingResult().getTarget()));
    return badRequest(new ErrorResponse("Validation failed", validationErrors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadableRequestBody(
      HttpMessageNotReadableException ex) {
    return badRequest(new ErrorResponse("Malformed request body", List.of()));
  }

  private ResponseEntity<ErrorResponse> badRequest(ErrorResponse errorResponse) {
    log.info("Responding with: {}", toJson(errorResponse));
    return ResponseEntity.badRequest().body(errorResponse);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }
}

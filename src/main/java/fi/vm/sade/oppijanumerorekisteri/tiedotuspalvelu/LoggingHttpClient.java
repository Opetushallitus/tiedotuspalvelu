package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static fi.vm.sade.cloudwatch.CloudWatchEMFEntry.FIELD_AWS_METADATA;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fi.vm.sade.cloudwatch.CloudWatchEMFEntry;
import fi.vm.sade.cloudwatch.MetricDefinition;
import fi.vm.sade.cloudwatch.MetricDirective;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingHttpClient {
  public static final String METRIC_NAMESPACE = "Tiedotuspalvelu";

  public static final Predicate<HttpResponse<?>> LOG_BODY_NEVER = response -> false;
  public static final Predicate<HttpResponse<?>> LOG_BODY_ALWAYS = response -> true;
  public static final Predicate<HttpResponse<?>> LOG_BODY_ON_ERROR =
      response -> response.statusCode() >= 400;

  private final String clientName;
  private final Predicate<HttpResponse<?>> defaultShouldLogResponseBody;
  private final ObjectMapper objectMapper;
  private final HttpClient delegate;

  public LoggingHttpClient(
      String clientName, Predicate<HttpResponse<?>> defaultShouldLogResponseBody) {
    this.clientName = clientName;
    this.defaultShouldLogResponseBody = defaultShouldLogResponseBody;
    this.objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.delegate = HttpClient.newHttpClient();
  }

  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
      throws IOException, InterruptedException {
    return send(request, bodyHandler, defaultShouldLogResponseBody);
  }

  public <T> HttpResponse<T> send(
      HttpRequest request,
      HttpResponse.BodyHandler<T> bodyHandler,
      Predicate<HttpResponse<?>> shouldLogResponseBody)
      throws IOException, InterruptedException {
    var requestTimestamp = Instant.now();
    var startNanos = System.nanoTime();
    int statusCode = -1;
    String body = null;
    try {
      var response = delegate.send(request, bodyHandler);
      statusCode = response.statusCode();
      if (shouldLogResponseBody.test(response) && response.body() instanceof String s) {
        body = s;
      }
      return response;
    } finally {
      var durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
      var logEntry =
          OutgoingRequestLog.builder()
              .client(clientName)
              .url(request.uri().toString())
              .httpCode(statusCode)
              .latency(durationMs)
              .timestamp(requestTimestamp)
              .body(body)
              .build();
      log.info(toJson(logEntry));
    }
  }

  private String toJson(OutgoingRequestLog entry) {
    try {
      return objectMapper.writeValueAsString(entry);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize outgoing request log entry, aborting process", e);
      System.exit(1);
      return null;
    }
  }

  @Builder
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record OutgoingRequestLog(
      @JsonProperty(FIELD_CLIENT) String client,
      String url,
      int httpCode,
      @JsonProperty(FIELD_LATENCY) long latency,
      Instant timestamp,
      String body) {
    public static final String FIELD_CLIENT = "client";
    public static final String FIELD_STATUS_CLASS = "statusClass";
    public static final String FIELD_LATENCY = "latency";

    @JsonProperty(FIELD_STATUS_CLASS)
    public String statusClass() {
      if (httpCode >= 500) {
        return "5XX";
      } else if (httpCode >= 400) {
        return "4XX";
      } else if (httpCode >= 300) {
        return "3XX";
      } else if (httpCode >= 200) {
        return "2XX";
      } else {
        return "Other";
      }
    }

    @JsonProperty(FIELD_AWS_METADATA)
    public CloudWatchEMFEntry makeCloudWatchEMFEntry() {
      return CloudWatchEMFEntry.builder()
          .timestamp(timestamp.toEpochMilli())
          .metricDirectives(
              List.of(
                  MetricDirective.builder()
                      .namespace(METRIC_NAMESPACE)
                      .dimensions(
                          List.of(
                              List.of(OutgoingRequestLog.FIELD_CLIENT),
                              List.of(
                                  OutgoingRequestLog.FIELD_CLIENT,
                                  OutgoingRequestLog.FIELD_STATUS_CLASS)))
                      .metrics(
                          List.of(
                              MetricDefinition.builder()
                                  .name(OutgoingRequestLog.FIELD_LATENCY)
                                  .unit(Optional.of("Milliseconds"))
                                  .build()))
                      .build()))
          .build();
    }
  }
}

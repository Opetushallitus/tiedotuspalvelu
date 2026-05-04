package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER;
import static fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.LoggingHttpClient.LOG_BODY_ALWAYS;
import static fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.LoggingHttpClient.LOG_BODY_NEVER;
import static fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.LoggingHttpClient.LOG_BODY_ON_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

public class LoggingHttpClientTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeEach
  public void setup() {
    wireMock.resetAll();
  }

  @Test
  public void logsJsonForSuccessfulResponse() throws Exception {
    var objectMapper = new ObjectMapper();
    var logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(listAppender);
    try {
      Stream.of(200, 301, 400, 500)
          .forEach(
              statusCode -> {
                listAppender.list.clear();
                var testPath = "/" + UUID.randomUUID();
                var testClientName = UUID.randomUUID().toString();
                wireMock.stubFor(
                    get(urlEqualTo(testPath))
                        .willReturn(aResponse().withStatus(statusCode).withBody("ok")));

                var httpClient = new LoggingHttpClient(testClientName, LOG_BODY_NEVER);
                var request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(wireMock.baseUrl() + testPath))
                        .GET()
                        .build();

                try {
                  httpClient.send(request, HttpResponse.BodyHandlers.discarding());

                  var message = singleLogMessage(listAppender);
                  var json = objectMapper.readTree(message);
                  assertEquals(testClientName, json.get("client").asText());
                  assertEquals(wireMock.baseUrl() + testPath, json.get("url").asText());
                  assertEquals(statusCode, json.get("httpCode").asInt());
                  assertTrue(json.get("duration").asLong() >= 0);
                  assertNotNull(Instant.parse(json.get("timestamp").asText()));
                  assertNull(json.get("body"));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  public void logsStringResponseBodyWhenDefaultIsAlways() throws Exception {
    var objectMapper = new ObjectMapper();
    var logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(listAppender);
    try {
      var testPath = "/" + UUID.randomUUID();
      var expectedBody = "hello from cat-server nyaa~";
      wireMock.stubFor(
          get(urlEqualTo(testPath)).willReturn(aResponse().withStatus(200).withBody(expectedBody)));

      var httpClient = new LoggingHttpClient(UUID.randomUUID().toString(), LOG_BODY_ALWAYS);
      var request =
          HttpRequest.newBuilder().uri(URI.create(wireMock.baseUrl() + testPath)).GET().build();

      httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      var json = objectMapper.readTree(singleLogMessage(listAppender));
      assertEquals(expectedBody, json.get("body").asText());
    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  public void skipsBodyForNonStringResponseEvenWhenDefaultIsAlways() throws Exception {
    var objectMapper = new ObjectMapper();
    var logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(listAppender);
    try {
      var testPath = "/" + UUID.randomUUID();
      wireMock.stubFor(
          get(urlEqualTo(testPath))
              .willReturn(aResponse().withStatus(200).withBody(new byte[] {1, 2, 3})));

      var httpClient = new LoggingHttpClient(UUID.randomUUID().toString(), LOG_BODY_ALWAYS);
      var request =
          HttpRequest.newBuilder().uri(URI.create(wireMock.baseUrl() + testPath)).GET().build();

      httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

      var json = objectMapper.readTree(singleLogMessage(listAppender));
      assertNull(json.get("body"));
    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  public void logBodyOnErrorSkipsSuccessButLogsErrors() throws Exception {
    var objectMapper = new ObjectMapper();
    var logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(listAppender);
    try {
      record Case(int statusCode, boolean expectBodyLogged) {}
      Stream.of(
              new Case(200, false), new Case(301, false), new Case(400, true), new Case(500, true))
          .forEach(
              testCase -> {
                listAppender.list.clear();
                var testPath = "/" + UUID.randomUUID();
                var responseBody = "response body for " + testCase.statusCode();
                wireMock.stubFor(
                    get(urlEqualTo(testPath))
                        .willReturn(
                            aResponse().withStatus(testCase.statusCode()).withBody(responseBody)));

                var httpClient =
                    new LoggingHttpClient(UUID.randomUUID().toString(), LOG_BODY_ON_ERROR);
                var request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(wireMock.baseUrl() + testPath))
                        .GET()
                        .build();

                try {
                  httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                  var json = objectMapper.readTree(singleLogMessage(listAppender));
                  if (testCase.expectBodyLogged()) {
                    assertEquals(responseBody, json.get("body").asText());
                  } else {
                    assertNull(json.get("body"));
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  public void perCallPredicateOverridesDefault() throws Exception {
    var objectMapper = new ObjectMapper();
    var logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(listAppender);
    try {
      var testPath = "/" + UUID.randomUUID();
      var responseBody = "sensitive nyaa~";
      wireMock.stubFor(
          get(urlEqualTo(testPath)).willReturn(aResponse().withStatus(200).withBody(responseBody)));

      var httpClient = new LoggingHttpClient(UUID.randomUUID().toString(), LOG_BODY_ALWAYS);
      var request =
          HttpRequest.newBuilder().uri(URI.create(wireMock.baseUrl() + testPath)).GET().build();

      httpClient.send(request, HttpResponse.BodyHandlers.ofString(), LOG_BODY_NEVER);

      var json = objectMapper.readTree(singleLogMessage(listAppender));
      assertNull(json.get("body"));
    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  public void logsJsonWhenSendThrows() throws Exception {
    var objectMapper = new ObjectMapper();
    var logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logger.addAppender(listAppender);
    try {
      var testClientName = UUID.randomUUID().toString();
      var testUrl = "/" + UUID.randomUUID();
      wireMock.stubFor(
          get(urlEqualTo(testUrl)).willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));

      var httpClient = new LoggingHttpClient(testClientName, LOG_BODY_NEVER);
      var request =
          HttpRequest.newBuilder().uri(URI.create(wireMock.baseUrl() + testUrl)).GET().build();

      assertThrows(
          IOException.class,
          () -> httpClient.send(request, HttpResponse.BodyHandlers.discarding()));

      var message = singleLogMessage(listAppender);
      var json = objectMapper.readTree(message);
      assertEquals(-1, json.get("httpCode").asInt());
    } finally {
      logger.detachAppender(listAppender);
      logger.setLevel(originalLevel);
    }
  }

  private static String singleLogMessage(ListAppender<ILoggingEvent> appender) {
    assertEquals(1, appender.list.size());
    return appender.list.get(0).getFormattedMessage();
  }
}

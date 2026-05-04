package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
public class AccessLogValveTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void accessLogIsWrittenInExpectedJsonFormatForRealRequest(CapturedOutput output)
      throws Exception {
    var userAgent = UUID.randomUUID().toString();
    var callerId = UUID.randomUUID().toString();
    var xForwardedFor = UUID.randomUUID().toString();
    var referer = UUID.randomUUID().toString();

    HttpHeaders headers = new HttpHeaders();
    headers.add("User-Agent", userAgent);
    headers.add("Caller-Id", callerId);
    headers.add("X-Forwarded-For", xForwardedFor);
    headers.add("Referer", referer);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/omat-viestit/actuator/health",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertEquals(200, response.getStatusCode().value());

    String accessLogLine = waitForAccessLogLine(output, "/omat-viestit/actuator/health");
    JsonNode json = new ObjectMapper().readTree(accessLogLine);

    assertEquals("GET", json.get("requestMethod").asText());
    assertEquals("GET /omat-viestit/actuator/health HTTP/1.1", json.get("request").asText());
    assertEquals("200", json.get("responseCode").asText());
    assertEquals(userAgent, json.get("user-agent").asText());
    assertEquals(callerId, json.get("caller-id").asText());
    assertEquals(xForwardedFor, json.get("x-forwarded-for").asText());
    assertEquals(referer, json.get("referer").asText());
    assertTrue(
        json.get("requestId")
            .asText()
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
        "requestId should be a UUID set by RequestIdFilter, was: "
            + json.get("requestId").asText());

    for (String field :
        new String[] {
          "timestamp",
          "requestId",
          "responseCode",
          "requestMapping",
          "request",
          "responseTime",
          "requestMethod",
          "service",
          "environment",
          "customer",
          "user-agent",
          "clientSubSystemCode",
          "callerHenkiloOid",
          "caller-id",
          "x-forwarded-for",
          "remote-ip",
          "session",
          "response-size",
          "referer",
          "opintopolku-api-key"
        }) {
      assertNotNull(json.get(field), "missing field in access log JSON: " + field);
    }
  }

  private static String waitForAccessLogLine(CapturedOutput output, String uriFragment) {
    return Awaitility.await()
        .atMost(2, TimeUnit.SECONDS)
        .pollInterval(20, TimeUnit.MILLISECONDS)
        .until(() -> findAccessLogLine(output, uriFragment), Optional::isPresent)
        .orElseThrow();
  }

  private static Optional<String> findAccessLogLine(CapturedOutput output, String uriFragment) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.startsWith("{") && line.contains(uriFragment))
        .reduce((first, second) -> second);
  }
}

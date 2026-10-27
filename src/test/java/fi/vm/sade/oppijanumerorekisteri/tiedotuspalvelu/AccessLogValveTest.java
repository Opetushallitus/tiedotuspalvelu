package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=none")
@AutoConfigureTestRestTemplate
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

    assertEquals("GET", json.required("requestMethod").stringValue());
    assertEquals(
        "GET /omat-viestit/actuator/health HTTP/1.1", json.required("request").stringValue());
    assertEquals("200", json.required("responseCode").stringValue());
    assertTrue(
        json.get("responseTime").isNumber(),
        "responseTime must be numeric so CloudWatch metric filters can extract it");
    assertTrue(
        json.get("response-size").isNumber(),
        "response-size must be numeric so CloudWatch metric filters can extract it");
    assertEquals(userAgent, json.required("user-agent").stringValue());
    assertEquals(callerId, json.required("caller-id").stringValue());
    assertEquals(xForwardedFor, json.required("x-forwarded-for").stringValue());
    assertEquals(referer, json.required("referer").stringValue());
    assertTrue(
        json.required("requestId")
            .stringValue()
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
        "requestId should be a UUID set by RequestIdFilter, was: "
            + json.required("requestId").stringValue());

    for (String field :
        new String[] {
          "timestamp",
          "requestId",
          "responseCode",
          "requestMapping",
          "request",
          "responseTime",
          "requestMethod",
          "user-agent",
          "callerHenkiloOid",
          "caller-id",
          "x-forwarded-for",
          "remote-ip",
          "session",
          "response-size",
          "referer"
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

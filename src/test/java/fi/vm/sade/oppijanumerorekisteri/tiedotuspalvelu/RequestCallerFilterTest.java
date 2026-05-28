package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(OutputCaptureExtension.class)
public class RequestCallerFilterTest extends TiedotuspalveluApiTest implements ResourceReader {

  @Autowired private TestRestTemplate restTemplate;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("tiedotuspalvelu.cas-oppija.server-url", wireMock::baseUrl);
    registry.add("tiedotuspalvelu.cas-virkailija.server-url", wireMock::baseUrl);
  }

  @BeforeEach
  public void setup() {
    clearDatabase();
    super.seedHenkiloFixture();
    seedHenkilo(OPPIJANUMERO_NORDEA_DEMO);
    wireMock.resetAll();
  }

  @Test
  public void logsCallerHenkiloOidWhenCallerAuthenticatedWithOauth2(CapturedOutput output)
      throws Exception {
    var createdTiedote = createTiedote(OPPIJANUMERO_NORDEA_DEMO);

    var userAgent = UUID.randomUUID().toString();
    var xForwardedFor = UUID.randomUUID().toString();
    var referer = UUID.randomUUID().toString();

    var token = fetchToken(KIELITUTKINNOSTA_TIEDOTTAJA);

    HttpHeaders headers = new HttpHeaders();
    headers.add("User-Agent", userAgent);
    headers.add("X-Forwarded-For", xForwardedFor);
    headers.add("Referer", referer);

    headers.setBearerAuth(token);

    restTemplate.exchange(
        "/omat-viestit/api/v1/tiedote/" + createdTiedote.getId(),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    assertThat(output).contains("\"callerHenkiloOid\": \"1.2.246.562.24.43006465835\"");
  }

  @Test
  public void logsCallerHenkiloOidWhenCallerAuthenticatedWithCasOppija(CapturedOutput output)
      throws IOException, InterruptedException {
    var ticket = "ST-30-JVB-gESc2Yc3S-zV25JOHbVEeBo-ip-10-0-55-20";
    var cookie = getCookie("/cas-oppija");
    // Stub cas-oppija endpoints
    wireMock.stubFor(
        get(urlEqualTo(
                "/login?service="
                    + URLEncoder.encode(
                        "http://localhost:8080/omat-viestit", StandardCharsets.UTF_8)))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "http://localhost:8080/omat-viestit/j_spring_cas_security_check?ticket="
                            + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    wireMock.stubFor(
        post(urlEqualTo(
                "/login?service="
                    + URLEncoder.encode(
                        "http://localhost:8080/omat-viestit/j_spring_cas_security_check",
                        StandardCharsets.UTF_8)))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "http://localhost:8080/omat-viestit/j_spring_cas_security_check?ticket="
                            + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    wireMock.stubFor(
        get(urlEqualTo(
                "/login?service="
                    + URLEncoder.encode(
                        "http://localhost:8080/omat-viestit/j_spring_cas_security_check",
                        StandardCharsets.UTF_8)))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "http://localhost:8080/omat-viestit/j_spring_cas_security_check?ticket="
                            + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    // validate ticket, provide cas response
    wireMock.stubFor(
        get(urlEqualTo(
                "/p3/serviceValidate?ticket=%s&service=%s"
                    .formatted(
                        ticket,
                        URLEncoder.encode(
                            "http://localhost:8080/omat-viestit/j_spring_cas_security_check",
                            StandardCharsets.UTF_8))))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(readResource("/cas-oppija-auth-response.xml"))));

    var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    var request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    wireMock.url(
                        "/login?service="
                            + URLEncoder.encode(
                                "http://localhost:8080/omat-viestit/j_spring_cas_security_check",
                                StandardCharsets.UTF_8))))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

    var responseHeaders = response.headers();

    var cookies = responseHeaders.allValues("Set-Cookie").get(0);

    var tiedotteetRequest =
        HttpRequest.newBuilder()
            .header("Cookie", cookies)
            .uri(URI.create("http://localhost:8080/omat-viestit/ui/tiedotteet"))
            .GET()
            .build();

    client.send(tiedotteetRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(output).contains("\"callerHenkiloOid\": \"1.2.246.562.98.19783284870\"");
  }

  private Cookie getCookie(String path) {
    var tgc = "TGC=asd";
    return new Cookie(
        path, tgc, "SameSite=none", "SameSite=None", "Secure", "HttpOnly", "Path=" + path);
  }

  @Test
  public void logsCallerHenkiloOidWhenCallerAuthenticatedWithCasVirkailija(CapturedOutput output)
      throws Exception {
    var cookie = getCookie("/cas-virkailija");
    var ticket = "ST-30-JVB-gESc2Yc3S-zV25JOHbVEeBo-ip-10-0-55-20";
    // Stub cas-virkailija endpoints
    wireMock.stubFor(
        get(urlEqualTo(
                "/login?service="
                    + URLEncoder.encode(
                        "http://localhost:8080/tiedotuspalvelu", StandardCharsets.UTF_8)))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check?ticket="
                            + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    wireMock.stubFor(
        post(urlEqualTo(
                "/login?service="
                    + URLEncoder.encode(
                        "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check",
                        StandardCharsets.UTF_8)))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check?ticket="
                            + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    wireMock.stubFor(
        get(urlEqualTo(
                "/login?service="
                    + URLEncoder.encode(
                        "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check",
                        StandardCharsets.UTF_8)))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location",
                        "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check?ticket="
                            + ticket)
                    .withHeader("Set-Cookie", cookie.toString())));
    // validate ticket, provide cas response
    wireMock.stubFor(
        get(urlEqualTo(
                "/p3/serviceValidate?ticket=%s&service=%s"
                    .formatted(
                        ticket,
                        URLEncoder.encode(
                            "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check",
                            StandardCharsets.UTF_8))))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(readResource("/cas-virkailija-auth-response.xml"))));

    var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    var request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    wireMock.url(
                        "/login?service="
                            + URLEncoder.encode(
                                "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check",
                                StandardCharsets.UTF_8))))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());

    var responseHeaders = response.headers();

    var cookies = responseHeaders.allValues("Set-Cookie").get(0);

    var summaryRequest =
        HttpRequest.newBuilder()
            .header("Cookie", cookies)
            .uri(URI.create("http://localhost:8080/tiedotuspalvelu/ui/tiedotteet/summary"))
            .GET()
            .build();

    client.send(summaryRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(output).contains("\"callerHenkiloOid\": \"1.2.246.562.98.1234567890\"");
  }
}

package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.URLDecoder;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(OutputCaptureExtension.class)
public class RequestCallerFilterTest extends TiedotuspalveluApiTest implements ResourceReader {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired TiedotuspalveluProperties properties;

  @LocalServerPort int port;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().extensions(CookieMirrorTransformer.class))
          .build();

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("tiedotuspalvelu.cas-oppija.server-url", wireMock::baseUrl);
    registry.add("tiedotuspalvelu.cas-virkailija.server-url", wireMock::baseUrl);
    // registry.add("tiedotuspalvelu.cas-oppija.service-base-url", );
    // registry.add("tiedotuspalvelu.suomifi-viestit.password", () -> SUOMIFI_PASSWORD);
    // registry.add("tiedotuspalvelu.suomifi-viestit.sender-service-id", () -> SUOMIFI_SYSTEM_ID);
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
      throws Exception {

    var tgc = "TGC=asd";
    var cookiePath = "/cas-oppija";
    var cookie =
        new Cookie(
            cookiePath,
            tgc,
            "SameSite=none",
            "SameSite=None",
            "Secure",
            "HttpOnly",
            "Path=/cas-oppija");

    System.out.println("cookie " + cookie);
    var ticket = "ST-30-JVB-gESc2Yc3S-zV25JOHbVEeBo-ip-10-0-55-20";
    var serviceUrl =
        URLEncoder.encode(
            restTemplate.getRootUri() + "/omat-viestit/j_spring_cas_security_check",
            StandardCharsets.UTF_8);
    var location =
        restTemplate.getRootUri() + "/omat-viestit/j_spring_cas_security_check?ticket=" + ticket;

    wireMock.stubFor(
        get(urlEqualTo("/login?service=" + serviceUrl))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader(
                        "Location", URLDecoder.decode(serviceUrl, StandardCharsets.UTF_8))));
    wireMock.stubFor(
        post(urlEqualTo("/omat-viestit/cas-oppija/login?service=tiedotuspalvelu"))
            .willReturn(
                aResponse()
                    .withStatus(302)
                    .withHeader("Location", location)
                    .withHeader("Set-Cookie", cookie.getValue())));
    wireMock.stubFor(
        get(urlEqualTo("/p3/serviceValidate?ticket=%s&service=%s".formatted(ticket, serviceUrl)))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(readResource("/cas-oppija-auth-response.xml"))));

    // TODO: copy virkailija implementation for this test
    assertThat(output).contains("\"callerHenkiloOid\": \"1.2.246.562.98.19783284870\"");
  }

  private Cookie getCookie() {
    var tgc = "TGC=asd";
    var cookiePath = "/cas-virkailija";
    return new Cookie(
        cookiePath,
        tgc,
        "SameSite=none",
        "SameSite=None",
        "Secure",
        "HttpOnly",
        "Path=/cas-virkailija");
  }

  private void createWiremockStubs(Cookie cookie, String ticketId) {
    // TODO: check which ones of these are actually necessary
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
                            + ticketId)
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
                            + ticketId)
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
                            + ticketId)
                    .withHeader("Set-Cookie", cookie.toString())));
    // validate, provide cas response
    wireMock.stubFor(
        get(urlEqualTo(
                "/p3/serviceValidate?ticket=%s&service=%s"
                    .formatted(
                        ticketId,
                        URLEncoder.encode(
                            "http://localhost:8080/tiedotuspalvelu/j_spring_cas_security_check",
                            StandardCharsets.UTF_8))))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(readResource("/cas-virkailija-auth-response.xml"))));
  }

  @Test
  public void logsCallerHenkiloOidWhenCallerAuthenticatedWithCasVirkailija(CapturedOutput output)
      throws Exception {
    var cookie = getCookie();
    var ticket = "ST-30-JVB-gESc2Yc3S-zV25JOHbVEeBo-ip-10-0-55-20";
    createWiremockStubs(cookie, ticket);

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

    var summaryResponse = client.send(summaryRequest, HttpResponse.BodyHandlers.ofString());
    System.out.println("summaryResponse: " + summaryResponse);

    assertThat(output).contains("\"callerHenkiloOid\": \"1.2.246.562.98.1234567890\"");
  }
}

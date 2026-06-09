package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.ValidateTiedoteTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.security.CasVirkailijaUserDetailsService;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SendSuomiFiViestitTask;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies the "Viesti välitetty Suomi.fi-viestit palveluun" column of the virkailija CSV report by
 * driving the real validate → send flow, so the report reflects values the tasks actually produce
 * (the viesti processed timestamp and the tiedote send-attempt count) rather than hand-seeded rows.
 */
public class CsvReportContentTest extends TiedotuspalveluApiTest implements ResourceReader {

  @Autowired private ValidateTiedoteTask validateTiedoteTask;
  @Autowired private SendSuomiFiViestitTask sendSuomiFiViestitTask;

  private static final String SUOMIFI_TOKEN = UUID.randomUUID().toString();
  private static final String SUOMIFI_MESSAGE_ID = UUID.randomUUID().toString();

  @RegisterExtension
  static WireMockExtension onrWireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension suomifiWireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("tiedotuspalvelu.oppijanumerorekisteri.base-url", onrWireMock::baseUrl);
    registry.add("tiedotuspalvelu.suomifi-viestit.base-url", suomifiWireMock::baseUrl);
    registry.add("tiedotuspalvelu.suomifi-viestit.username", () -> "test-username");
    registry.add("tiedotuspalvelu.suomifi-viestit.password", () -> "test-password");
    registry.add(
        "tiedotuspalvelu.suomifi-viestit.sender-service-id", () -> "test-sender-service-id");
  }

  @BeforeEach
  public void setup() {
    clearDatabase();
    onrWireMock.resetAll();
    suomifiWireMock.resetAll();
    onrWireMock.stubFor(
        WireMock.get(urlPathMatching("/henkilo/.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        readResource("/henkilo/" + OPPIJANUMERO_HELLIN_SEVILLANTES + ".json"))));
    suomifiWireMock.stubFor(
        post(urlEqualTo("/v1/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\": \"%s\", \"expires_in\": 3600}"
                            .formatted(SUOMIFI_TOKEN))));
  }

  @Test
  public void csvShowsSuomiFiDeliveryTimestampForSentViesti() throws Exception {
    suomifiWireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var tiedote = createTiedote(OPPIJANUMERO_HELLIN_SEVILLANTES);

    validateTiedoteTask.execute();
    var validated = tiedoteRepository.findById(tiedote.getId()).orElseThrow();
    assertThat(validated.getState()).isEqualTo(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS);
    assertThat(validated.getViesti()).isNotNull();

    sendSuomiFiViestitTask.execute();
    var sent = tiedoteRepository.findById(tiedote.getId()).orElseThrow();
    assertThat(sent.getState()).isEqualTo(Tiedote.STATE_TIEDOTE_KÄSITELTY);
    assertThat(sent.getViesti().getProcessedAt()).isNotNull();

    var deliveryDate =
        sent.getViesti()
            .getProcessedAt()
            .atZoneSameInstant(ZoneId.of("Europe/Helsinki"))
            .toLocalDate()
            .toString();

    var csv = downloadCsv();
    assertThat(csv).doesNotContain("Ei välitetty");
    assertThat(csv).contains(deliveryDate);
  }

  @Test
  public void csvShowsSendAttemptCountForUndeliveredViesti() throws Exception {
    suomifiWireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic")).willReturn(aResponse().withStatus(500)));

    var tiedote = createTiedote(OPPIJANUMERO_HELLIN_SEVILLANTES);

    validateTiedoteTask.execute();
    assertThat(tiedoteRepository.findById(tiedote.getId()).orElseThrow().getState())
        .isEqualTo(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS);

    sendSuomiFiViestitTask.execute();
    // The first failure schedules a backoff; simulate the scheduler waiting it out before retrying.
    expireNextRetry(tiedote.getId());
    sendSuomiFiViestitTask.execute();

    var failed = tiedoteRepository.findById(tiedote.getId()).orElseThrow();
    assertThat(failed.getViesti().getProcessedAt()).isNull();
    assertThat(failed.getRetryCount()).isEqualTo(2);

    var csv = downloadCsv();
    assertThat(csv).contains("Ei välitetty; yritetty 2 kertaa");
  }

  private void expireNextRetry(UUID tiedoteId) {
    var tiedote = tiedoteRepository.findById(tiedoteId).orElseThrow();
    tiedote.setNextRetry(OffsetDateTime.now().minusMinutes(5));
    tiedoteRepository.save(tiedote);
  }

  private String downloadCsv() throws Exception {
    var bytes =
        mockMvc
            .perform(
                get("/tiedotuspalvelu/ui/tiedotteet/csv")
                    .with(user(VIRKAILIJA_WITH_RAPORTOINTI_ROLE)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private final CasVirkailijaUserDetailsService.CasAuthenticatedUser
      VIRKAILIJA_WITH_RAPORTOINTI_ROLE =
          CasVirkailijaUserDetailsService.CasAuthenticatedUser.builder()
              .username("riinaraportoija")
              .attributes(
                  Map.of(
                      "oidHenkilo",
                      List.of("1.2.246.562.24.80170786687"),
                      "kayttajaTyyppi",
                      List.of("VIRKAILIJA"),
                      "idpEntityId",
                      List.of("usernamePassword")))
              .authorities(
                  List.of(
                      new SimpleGrantedAuthority("ROLE_APP_TIEDOTUSPALVELU_RAPORTOINTI"),
                      new SimpleGrantedAuthority(
                          "ROLE_APP_TIEDOTUSPALVELU_RAPORTOINTI_" + OPH_ORGANISAATIO_OID)))
              .build();
}

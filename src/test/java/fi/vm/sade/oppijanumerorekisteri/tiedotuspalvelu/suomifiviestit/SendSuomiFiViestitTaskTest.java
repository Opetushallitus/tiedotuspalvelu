package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.ResourceReader;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.Tiedote;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluApiTest;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluProperties;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(OutputCaptureExtension.class)
public class SendSuomiFiViestitTaskTest extends TiedotuspalveluApiTest implements ResourceReader {

  @Autowired private SendSuomiFiViestitTask sendSuomiFiViestitTask;

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String SUOMIFI_USERNAME = UUID.randomUUID().toString();
  private static final String SUOMIFI_PASSWORD = UUID.randomUUID().toString();
  private static final String SUOMIFI_SYSTEM_ID = UUID.randomUUID().toString();
  private static final String SUOMIFI_TOKEN = UUID.randomUUID().toString();
  private static final String SUOMIFI_MESSAGE_ID = UUID.randomUUID().toString();
  @Autowired private TiedotuspalveluProperties tiedotuspalveluProperties;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("tiedotuspalvelu.suomifi-viestit.base-url", wireMock::baseUrl);
    registry.add("tiedotuspalvelu.suomifi-viestit.username", () -> SUOMIFI_USERNAME);
    registry.add("tiedotuspalvelu.suomifi-viestit.password", () -> SUOMIFI_PASSWORD);
    registry.add("tiedotuspalvelu.suomifi-viestit.sender-service-id", () -> SUOMIFI_SYSTEM_ID);
  }

  @BeforeEach
  public void setup() {
    clearDatabase();
    wireMock.resetAll();
  }

  @Test
  public void sendsTheExpectedElectronicMessageToSuomiFiViestit() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));
    var tiedote = createTiedoteAndRunTask();

    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages/electronic"))
            .withHeader("Authorization", equalTo("Bearer " + SUOMIFI_TOKEN))
            .withRequestBody(matchingJsonPath("$.externalId", equalTo(tiedote.getId().toString())))
            .withRequestBody(matchingJsonPath("$.recipient.id", equalTo("010170-9998")))
            .withRequestBody(matchingJsonPath("$.sender.serviceId", equalTo(SUOMIFI_SYSTEM_ID)))
            .withRequestBody(
                matchingJsonPath(
                    "$.electronic.title",
                    equalTo(
                        "Uusi viesti Oma Opintopolussa | Nytt meddelande i Min Studieinfo | New message in My Studyinfo")))
            .withRequestBody(
                matchingJsonPath(
                    "$.electronic.body",
                    equalTo(
                        "Hei! \n\nSinulle on saapunut uusi viesti Oma Opintopolku-palveluun. Voit lukea viestin kirjautumalla Oma Opintopolkuun. Löydät sinulle saapuneet viestit Viestini-sivulta. \n\nViesti koskee seuraavaa asiaa: \nTodistus (Yleiset kielitutkinnot, YKI) \n\nTietoturvan takia tässä viestissä ei ole suoraa linkkiä Oma Opintopolku-palveluun.\n\nYstävällisin terveisin, \nOpetushallitus\n\n-\n\nHej!\n\nDu har fått ett nytt meddelande i tjänsten Min Studieinfo. Du kan läsa meddelandet genom att logga in i tjänsten. Du hittar dina mottagna meddelanden på sidan Mina meddelanden.\n\nMeddelandet gäller följande ärende:\nExamensintyg (Allmänna språkexamina, YKI)\n\nAv informationssäkerhetsskäl innehåller detta meddelande ingen direkt länk till tjänsten Min Studieinfo.\n\nVänliga hälsningar,\nUtbildningsstyrelsen\n\n- \n\nHi!\n\nYou have received a new message in the My Studyinfo service. You can read the message by logging in to the service. View your messages on the My Messages page.\n\nThe message concerns the following matter:\nCertificate (National Certificates of Language Proficiency, YKI)\n\nFor information security reasons, this message does not contain a direct link to the My Studyinfo service.\n\nBest regards,\nFinnish National Agency for Education (EDUFI)"))));
  }

  @Test
  public void respectsNextRetryTimeInFuture() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var tiedote =
        createTiedoteAndRunTask(
            t -> {
              t.setRetryCount(1);
              t.setNextRetry(OffsetDateTime.now().plusHours(1));
            });

    assertEquals(1, tiedote.getRetryCount());
    assertThat(tiedote.getNextRetry()).isAfter(OffsetDateTime.now());
    assertThat(tiedote.getViesti().getProcessedAt()).isNull();
  }

  @Test
  public void respectsNextRetryTimeInPast() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var tiedote =
        createTiedoteAndRunTask(
            t -> {
              t.setRetryCount(1);
              t.setNextRetry(OffsetDateTime.now().minusDays(10));
            });

    assertEquals(0, tiedote.getRetryCount());
    assertThat(tiedote.getNextRetry()).isNull();
    assertThat(tiedote.getViesti().getProcessedAt()).isNotNull();
  }

  @Test
  public void handlesSuomiFiFailure() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic")).willReturn(aResponse().withStatus(500)));

    var updatedTiedote = createTiedoteAndRunTask();
    assertNull(updatedTiedote.getViesti().getProcessedAt());
    assertNull(updatedTiedote.getViesti().getMessageId());
    assertEquals(1, updatedTiedote.getRetryCount());
    assertNotNull(updatedTiedote.getNextRetry());
  }

  private Tiedote createTiedoteAndRunTask() throws Exception {
    return createTiedoteAndRunTask(t -> {});
  }

  private Tiedote createTiedoteAndRunTask(Consumer<Tiedote> modify) throws Exception {
    var tiedote = createTiedote("1.2.3");
    tiedote.setState(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS);
    tiedote.setViesti(getSuomiFiViestiBuilder(tiedote).build());
    modify.accept(tiedote);
    tiedote = tiedoteRepository.save(tiedote);

    sendSuomiFiViestitTask.execute();

    return tiedoteRepository.findById(tiedote.getId()).orElseThrow();
  }

  @Test
  public void switchesToKielitutkintotodistuksenNoutoForPaperMailingOnMailboxNotInUse()
      throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"errorCode\": \"MAILBOX_NOT_IN_USE\"}")));

    var tiedote = createTiedoteAndRunTask();

    sendSuomiFiViestitTask.execute();

    var updatedTiedote = tiedoteRepository.findById(tiedote.getId()).orElseThrow();
    assertEquals(
        SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL,
        updatedTiedote.getViesti().getMessageType());
    assertNull(updatedTiedote.getViesti().getProcessedAt());
    assertNull(updatedTiedote.getNextRetry());
    assertEquals(0, updatedTiedote.getRetryCount());
    assertThat(updatedTiedote.getState()).isEqualTo(Tiedote.STATE_KIELITUTKINTOTODISTUKSEN_NOUTO);
  }

  @Test
  public void sendsPaperMailMessage() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/attachments"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"attachmentId\": \"attach-123\"}")));
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"messageId\": \"msg-456\"}")));

    var updatedTiedote =
        createTiedoteAndRunTask(
            t -> {
              t.setState(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA);
              t.setTodistusBucketName("bucketName");
              t.setTodistusObjectKey("objectKey");
              t.setKielitutkintotodistusPdf(
                  KielitutkintotodistusPdf.builder()
                      .tiedote(t)
                      .content(readBytes("/fakekielitutkintotodistus.pdf"))
                      .build());
              t.getViesti().setName("Testi Testaaja Testiläinen");
              t.getViesti().setMessageType(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL);
              t.getViesti().setStreetAddress("not a real french address");
              t.getViesti().setZipCode("75008");
              t.getViesti().setCity("PARIS");
              t.getViesti().setCountryCode("FR");
            });

    assertEquals(
        SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL,
        updatedTiedote.getViesti().getMessageType());
    assertNotNull(updatedTiedote.getViesti().getProcessedAt());
    assertEquals("msg-456", updatedTiedote.getViesti().getMessageId());
    assertEquals(0, updatedTiedote.getRetryCount());

    wireMock.verify(postRequestedFor(urlEqualTo("/v2/attachments")));
    wireMock.verify(postRequestedFor(urlEqualTo("/v2/messages")));
    var attachmentsJson = "[{\"attachmentId\":\"attach-123\"}]";
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages"))
            .withRequestBody(
                matchingJsonPath("$.paperMail.attachments", equalToJson(attachmentsJson)))
            .withRequestBody(matchingJsonPath("$.paperMail.colorPrinting", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.paperMail.createAddressPage", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.paperMail.messageServiceType", equalTo("Normal")))
            .withRequestBody(matchingJsonPath("$.paperMail.twoSidedPrinting", equalTo("true")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.printingAndEnvelopingService.costPool", absent()))
            .withRequestBody(
                matchingJsonPath(
                    "$.paperMail.printingAndEnvelopingService.postiMessaging.username",
                    equalTo("posti-username")))
            .withRequestBody(
                matchingJsonPath(
                    "$.paperMail.printingAndEnvelopingService.postiMessaging.password",
                    equalTo("posti-password")))
            .withRequestBody(
                matchingJsonPath(
                    "$.paperMail.recipient.address.name", equalTo("Testi Testaaja Testiläinen")))
            .withRequestBody(
                matchingJsonPath(
                    "$.paperMail.recipient.address.streetAddress",
                    equalTo("not a real french address")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.zipCode", equalTo("75008")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.city", equalTo("PARIS")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.countryCode", equalTo("FR"))));
  }

  @Test
  @Transactional
  @Sql(
      value = {"/data/localisations.sql"},
      executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
  public void usesTodistuskieliFromTiedoteAsLocalisationLanguage() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var swedishTiedote = createTiedoteAndRunTask(t -> t.setTodistuskieli("SV"));
    assertThat(swedishTiedote.getViesti().getOtsikko()).isEqualTo("(SV) tiedote viesti otsikko");
    assertThat(swedishTiedote.getViesti().getSisalto()).isEqualTo("(SV) tiedote viesti sisältö");
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages/electronic"))
            .withRequestBody(
                matchingJsonPath("$.electronic.title", equalTo("(SV) tiedote viesti otsikko")))
            .withRequestBody(
                matchingJsonPath("$.electronic.body", equalTo("(SV) tiedote viesti sisältö"))));

    var englishTiedote = createTiedoteAndRunTask(t -> t.setTodistuskieli("EN"));
    assertThat(englishTiedote.getViesti().getOtsikko()).isEqualTo("(EN) tiedote viesti otsikko");
    assertThat(englishTiedote.getViesti().getSisalto()).isEqualTo("(EN) tiedote viesti sisältö");
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages/electronic"))
            .withRequestBody(
                matchingJsonPath("$.electronic.title", equalTo("(EN) tiedote viesti otsikko")))
            .withRequestBody(
                matchingJsonPath("$.electronic.body", equalTo("(EN) tiedote viesti sisältö"))));

    var finnishTiedote = createTiedoteAndRunTask(t -> t.setTodistuskieli("FI"));
    assertThat(finnishTiedote.getViesti().getOtsikko()).isEqualTo("(FI) tiedote viesti otsikko");
    assertThat(finnishTiedote.getViesti().getSisalto()).isEqualTo("(FI) tiedote viesti sisältö");
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages/electronic"))
            .withRequestBody(
                matchingJsonPath("$.electronic.title", equalTo("(FI) tiedote viesti otsikko")))
            .withRequestBody(
                matchingJsonPath("$.electronic.body", equalTo("(FI) tiedote viesti sisältö"))));
  }

  @Test
  @Transactional
  @Sql(
      value = {"/data/localisations.sql"},
      executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
  public void useDefaultLanguageFinnishIfTodistuskieliIsNull() throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var defaultFinnishTiedote = createTiedoteAndRunTask(t -> t.setTodistuskieli(null));
    assertThat(defaultFinnishTiedote.getViesti().getOtsikko())
        .isEqualTo("(FI) tiedote viesti otsikko");
    assertThat(defaultFinnishTiedote.getViesti().getSisalto())
        .isEqualTo("(FI) tiedote viesti sisältö");
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages/electronic"))
            .withRequestBody(
                matchingJsonPath("$.electronic.title", equalTo("(FI) tiedote viesti otsikko")))
            .withRequestBody(
                matchingJsonPath("$.electronic.body", equalTo("(FI) tiedote viesti sisältö"))));
  }

  @Test
  public void sendsNonHetuSuomiFiMessagesToCorrectSuomiFiEndpoint(CapturedOutput output)
      throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/attachments"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"attachmentId\": \"attach-123\"}")));
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"messageId\": \"msg-456\"}")));
    wireMock.stubFor(
        post(urlEqualTo("/v2/paper-mail-without-id"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"messageId\": \"msg-456\"}")));

    var tiedote =
        createTiedoteAndRunTask(
            t -> {
              t.setViesti(
                  getSuomiFiViestiBuilder(t)
                      .messageType(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL)
                      .henkilotunnus(null)
                      .build());
              t.setKielitutkintotodistusPdf(
                  KielitutkintotodistusPdf.builder()
                      .tiedote(t)
                      .content(readBytes("/fakekielitutkintotodistus.pdf"))
                      .build());
              t.setState(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA);
            });

    wireMock.verify(0, postRequestedFor(urlEqualTo("/v2/messages")));
    wireMock.verify(1, postRequestedFor(urlEqualTo("/v2/attachments")));
    wireMock.verify(1, postRequestedFor(urlEqualTo("/v2/paper-mail-without-id")));
    Assertions.assertThat(output)
        .contains(
            "Sent Suomi.fi paper mail viesti (without id) for tiedote %s"
                .formatted(tiedote.getId()));
    var attachmentsJson = "[{\"attachmentId\": \"attach-123\"}]";
    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/paper-mail-without-id"))
            .withRequestBody(matchingJsonPath("$.externalId", equalTo(tiedote.getId().toString())))
            .withRequestBody(
                matchingJsonPath("$.paperMail.attachments", equalToJson(attachmentsJson)))
            .withRequestBody(matchingJsonPath("$.paperMail.colorPrinting", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.paperMail.createAddressPage", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.paperMail.messageServiceType", equalTo("Normal")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.city", equalTo("Espoo")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.countryCode", equalTo("FI")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.name", equalTo("Katti Purr")))
            .withRequestBody(
                matchingJsonPath(
                    "$.paperMail.recipient.address.streetAddress", equalTo("Kissatie 2")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.zipCode", equalTo("00200")))
            .withRequestBody(matchingJsonPath("$.paperMail.twoSidedPrinting", equalTo("true")))
            .withRequestBody(
                matchingJsonPath(
                    "$.sender.serviceId",
                    equalTo(tiedotuspalveluProperties.suomifiViestit().senderServiceId()))));

    var updated = tiedoteRepository.findById(tiedote.getId());
    assertThat(updated.get().getState()).isEqualTo(Tiedote.STATE_TIEDOTE_KÄSITELTY);
  }

  @Test
  public void failsToProcessTiedoteIfNonHetuRequestErrors(CapturedOutput output) throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/attachments"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"attachmentId\": \"attach-123\"}")));

    wireMock.stubFor(
        post(urlEqualTo("/v2/paper-mail-without-id"))
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"reason\": \"The message should be about the external ID already being in use\"}")));

    var tiedote =
        createTiedoteAndRunTask(
            t -> {
              t.setViesti(
                  getSuomiFiViestiBuilder(t)
                      .messageType(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL)
                      .henkilotunnus(null)
                      .build());
              t.setKielitutkintotodistusPdf(
                  KielitutkintotodistusPdf.builder()
                      .tiedote(t)
                      .content(readBytes("/fakekielitutkintotodistus.pdf"))
                      .build());
              t.setState(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA);
            });

    wireMock.verify(1, postRequestedFor(urlEqualTo("/v2/attachments")));
    wireMock.verify(1, postRequestedFor(urlEqualTo("/v2/paper-mail-without-id")));

    var updated = tiedoteRepository.findById(tiedote.getId());
    assertThat(updated.get().getState())
        .isEqualTo(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA);

    Assertions.assertThat(output)
        .contains(
            "java.lang.IllegalStateException: Suomi.fi viestit message call failed with status 409");
  }

  private void stubGettingSuomiFiViestitAccessToken() {
    wireMock.stubFor(
        post(urlEqualTo("/v1/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\": \"%s\", \"expires_in\": 3600}"
                            .formatted(SUOMIFI_TOKEN))));
  }

  private SuomiFiViesti.SuomiFiViestiBuilder getSuomiFiViestiBuilder(Tiedote tiedote) {
    return SuomiFiViesti.builder()
        .tiedote(tiedote)
        .henkilotunnus("010170-9998")
        .name("Katti Purr")
        .streetAddress("Kissatie 2")
        .zipCode("00200")
        .city("Espoo")
        .countryCode("FI")
        .messageType(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_ELECTRONIC);
  }
}

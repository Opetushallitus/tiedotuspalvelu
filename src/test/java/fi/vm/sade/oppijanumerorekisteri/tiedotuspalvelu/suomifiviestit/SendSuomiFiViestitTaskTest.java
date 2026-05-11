package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.ResourceReader;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.Tiedote;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluApiTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                        "Hei! \n\nSinulle on saapunut Oma Opintopolkuun uusi viesti. Voit lukea viestin kirjautumalla Oma Opintopolku-palveluun. Löydät sinulle saapuneet viestit Viestini-sivulta. \n\nViesti koskee seuraavaa asiaa: \nTodistus (Yleiset kielitutkinnot, YKI) \n\nTietoturvan takia viestissä ei ole suoraa linkkiä palveluun.\n\nTerveisin, \nOpetushallitus\n\n-\n\nHej! \n\nDu har fått ett nytt meddelande i My Studyinfo. Du kan läsa meddelandet genom att logga in på Min Studieinfo-tjänsten. Du hittas de meddelanden du har fått på sidan Mina meddelanden.\n\nMeddelandet gäller den följande saken: \nExamensintyg (Allmänna språkexamina, YKI)\n\nFör att värna om informationssäkerheten finns det ingen direkt länk till tjänsten i meddelandet.\n\nHälsningar, \nUtbildningsstyrelsen\n\n-\n\nHi! \n\nYou have received a new message in My Studyinfo service. You can read the message by logging in to the My Studyinfo service. You find the message on the My Messages page. \n\nThe message concerns the following matter: \nCertificate (National Certificates of Language Proficiency, YKI)\n\nFor reasons of information security, this message does not contain a direct link to the service.\n\nBest regards,\nFinnish National Agency for Education’s (EDUFI)"))));
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
    assertEquals("paperMail", updatedTiedote.getViesti().getMessageType());
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
              t.getViesti().setMessageType("paperMail");
            });

    assertEquals("paperMail", updatedTiedote.getViesti().getMessageType());
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
                    equalTo("posti-password"))));
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
  public void setsKituDerivedPostalInformationToSuomiFiViestiWhenProcessingElectronicMessages()
      throws Exception {
    stubGettingSuomiFiViestitAccessToken();
    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var kituPostalInfoElectronicTiedote =
        createTiedoteAndRunTask(
            t -> {
              t.getViesti().setMessageType("electronic");
              t.setMaakoodi("FRA");
              t.setKituKatuosoite("Not a real french street address 1 A 2");
              t.setKituPostinumero("75008");
              t.setKituPostitoimipaikka("PARIS");
            });
    assertThat(kituPostalInfoElectronicTiedote.getViesti().getCountryCode()).isEqualTo("FR");
    assertThat(kituPostalInfoElectronicTiedote.getViesti().getStreetAddress())
        .isEqualTo("Not a real french street address 1 A 2");
    assertThat(kituPostalInfoElectronicTiedote.getViesti().getZipCode()).isEqualTo("75008");
    assertThat(kituPostalInfoElectronicTiedote.getViesti().getCity()).isEqualTo("PARIS");
  }

  @Test
  public void usesKituDerivedPostalInformationWhenProcessingPaperMail() throws Exception {
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

    var kituPostalInfoPaperMailTiedote =
        createTiedoteAndRunTask(
            t -> {
              // State related attributes
              t.setState(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA);
              t.getViesti().setMessageType("paperMail");
              // Postal information
              t.setMaakoodi("FRA");
              t.setKituKatuosoite("Not a real french street address 1 A 2");
              t.setKituPostinumero("75008");
              t.setKituPostitoimipaikka("PARIS");
              // Attachment
              t.setTodistusBucketName("bucketName");
              t.setTodistusObjectKey("objectKey");
              t.setKielitutkintotodistusPdf(
                  KielitutkintotodistusPdf.builder()
                      .tiedote(t)
                      .content(readBytes("/fakekielitutkintotodistus.pdf"))
                      .build());
            });
    assertThat(kituPostalInfoPaperMailTiedote.getViesti().getMessageType()).isEqualTo("paperMail");
    assertThat(kituPostalInfoPaperMailTiedote.getViesti().getCountryCode()).isEqualTo("FR");
    assertThat(kituPostalInfoPaperMailTiedote.getViesti().getStreetAddress())
        .isEqualTo("Not a real french street address 1 A 2");
    assertThat(kituPostalInfoPaperMailTiedote.getViesti().getZipCode()).isEqualTo("75008");
    assertThat(kituPostalInfoPaperMailTiedote.getViesti().getCity()).isEqualTo("PARIS");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/v2/messages"))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.name", equalTo("Katti Purr")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.city", equalTo("PARIS")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.countryCode", equalTo("FR")))
            .withRequestBody(
                matchingJsonPath(
                    "$.paperMail.recipient.address.streetAddress",
                    equalTo("Not a real french street address 1 A 2")))
            .withRequestBody(
                matchingJsonPath("$.paperMail.recipient.address.zipCode", equalTo("75008"))));
  }

  @Test
  public void processingElectronicMessageFailsIfTiedoteIsMissingKituPostalInformation(
      CapturedOutput output) throws Exception {
    stubGettingSuomiFiViestitAccessToken();

    wireMock.stubFor(
        post(urlEqualTo("/v2/messages/electronic"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("{\"messageId\": \"%s\"}".formatted(SUOMIFI_MESSAGE_ID))));

    var tiedoteWithMissingPostalInfo =
        createTiedoteAndRunTask(
            t -> {
              t.setMaakoodi(null);
              t.setKituKatuosoite("not missing address 1 a 2");
              t.setKituPostinumero(null);
              t.setKituPostitoimipaikka("NIL");
            });

    // Throws and logs an error if data is missing
    Assertions.assertThat(output)
        .containsPattern("java.lang.IllegalArgumentException: Tiedote kitu postinumero is null");
    // Should not send requests to Suomi.fi
    wireMock.verify(0, postRequestedFor(urlEqualTo("/v2/messages/electronic")));
    // Should not update Tiedote.viesti, the values should be what they were before
    var updated = tiedoteRepository.findById(tiedoteWithMissingPostalInfo.getId());
    assertThat(updated.get().getViesti().getCountryCode()).isEqualTo("FI");
    assertThat(updated.get().getViesti().getStreetAddress()).isEqualTo("Kissatie 2");
    assertThat(updated.get().getViesti().getZipCode()).isEqualTo("00200");
    assertThat(updated.get().getViesti().getCity()).isEqualTo("Espoo");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS,
        Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA
      })
  public void processingPaperMailMessageFailsIfKituPostalInformationFieldsAreMissing(
      String state, CapturedOutput output) throws Exception {
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

    var tiedoteWithMissingPostalInfo =
        createTiedoteAndRunTask(
            t -> {
              t.setViesti(getSuomiFiViestiBuilder(t).messageType("paperMail").build());
              t.setKielitutkintotodistusPdf(
                  KielitutkintotodistusPdf.builder()
                      .tiedote(t)
                      .content(readBytes("/fakekielitutkintotodistus.pdf"))
                      .build());
              t.setState(state);
              t.setMaakoodi(null);
              t.setKituKatuosoite("not missing address 1 a 2");
              t.setKituPostinumero("00100");
              t.setKituPostitoimipaikka("NIL");
            });

    Assertions.assertThat(output)
        .containsPattern("java.lang.IllegalArgumentException: Tiedote maakoodi is null");
    wireMock.verify(0, postRequestedFor(urlEqualTo("/v2/messages")));
    wireMock.verify(0, postRequestedFor(urlEqualTo("/v2/attachments")));
    var updated = tiedoteRepository.findById(tiedoteWithMissingPostalInfo.getId());
    assertThat(updated.get().getViesti().getCountryCode()).isEqualTo("FI");
    assertThat(updated.get().getViesti().getStreetAddress()).isEqualTo("Kissatie 2");
    assertThat(updated.get().getViesti().getZipCode()).isEqualTo("00200");
    assertThat(updated.get().getViesti().getCity()).isEqualTo("Espoo");
  }

  private static Stream<Arguments> provideMissingKituPostalInfoFields() {
    Consumer<Tiedote> katuosoiteMissing =
        t -> {
          t.setKituKatuosoite(null);
        };
    Consumer<Tiedote> postinumeroMissing =
        t -> {
          t.setKituPostinumero(null);
        };
    Consumer<Tiedote> postitoimipaikkaMissing =
        t -> {
          t.setKituPostitoimipaikka(null);
        };
    Consumer<Tiedote> maakoodiMissing =
        t -> {
          t.setMaakoodi(null);
        };

    return Stream.of(
        Arguments.of(
            katuosoiteMissing,
            "java.lang.IllegalArgumentException: Tiedote kitu katuosoite is null"),
        Arguments.of(
            postinumeroMissing,
            "java.lang.IllegalArgumentException: Tiedote kitu postinumero is null"),
        Arguments.of(
            postitoimipaikkaMissing,
            "java.lang.IllegalArgumentException: Tiedote kitu postitoimipaikka is null"),
        Arguments.of(
            maakoodiMissing, "java.lang.IllegalArgumentException: Tiedote maakoodi is null"));
  }

  @ParameterizedTest
  @MethodSource("provideMissingKituPostalInfoFields")
  public void throwsAndLogsErrorIfKituPostalInformationFieldsAreMissing(
      Consumer<Tiedote> modifyTiedote, String expectedExceptionMessage, CapturedOutput output)
      throws Exception {
    createTiedoteAndRunTask(modifyTiedote);

    Assertions.assertThat(output).containsPattern(expectedExceptionMessage);
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
        .messageType("electronic");
  }
}

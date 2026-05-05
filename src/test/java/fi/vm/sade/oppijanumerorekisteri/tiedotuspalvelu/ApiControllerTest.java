package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api.KituExamineeDetailsDto;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api.KituKoodiarvoDto;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ExtendWith(OutputCaptureExtension.class)
public class ApiControllerTest extends TiedotuspalveluApiTest {

  private static final String OPPIJANUMERO = OidGenerator.generateHenkiloOid();
  private static final String OPISKELUOIKEUS_OID = OidGenerator.generateOpiskeluoikeusOid();

  @Test
  public void createTiedoteRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/omat-viestit/api/v1/tiedote/kielitutkintotodistus")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void createTiedoteFailsWithoutRequiredRole() throws Exception {
    mockMvc
        .perform(
            post("/omat-viestit/api/v1/tiedote/kielitutkintotodistus")
                .with(tokenFor(OIKEUDETON))
                .contentType(MediaType.APPLICATION_JSON)
                .content(tiedoteJson(UUID.randomUUID().toString(), "fi")))
        .andExpect(status().isForbidden());
  }

  @Test
  public void createTiedoteSucceedsWithValidData() throws Exception {
    clearDatabase();
    String idempotencyKey = UUID.randomUUID().toString();
    var returnedId = postTiedoteAndReturnId(tiedoteJson(idempotencyKey, "fi"));

    List<Tiedote> tiedotteet = tiedoteRepository.findAll();
    assertEquals(1, tiedotteet.size());
    Tiedote saved = tiedotteet.stream().filter(t -> t.getId().equals(returnedId)).findFirst().get();
    assertEquals(saved.getId(), returnedId);
    assertEquals(OPPIJANUMERO, saved.getOppijanumero());
    assertEquals(idempotencyKey, saved.getIdempotencyKey());

    mockMvc
        .perform(
            get("/omat-viestit/api/v1/tiedote/" + returnedId)
                .with(tokenFor(KIELITUTKINNOSTA_TIEDOTTAJA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(returnedId.toString()))
        .andExpect(jsonPath("$.opiskeluoikeusOid").value(OPISKELUOIKEUS_OID))
        .andExpect(jsonPath("$.meta.type").value("KIELITUTKINTOTODISTUS"))
        .andExpect(jsonPath("$.meta.state").value("OPPIJAN_VALIDOINTI"))
        .andExpect(jsonPath("$.statuses[0].status").value("CREATED"))
        .andExpect(jsonPath("$.statuses[0].timestamp").exists());
  }

  @Test
  public void createTiedoteSucceedsWithoutOpiskeluoikeusOid() throws Exception {
    var idempotencyKey = UUID.randomUUID().toString();
    var kituExamineeDetails = kituExamineeDetailsJson("fi");
    var body =
        """
        {
          "oppijanumero": "%s",
          "todistusBucket": "bucket",
          "todistusKey": "%s/todistus.pdf",
          "idempotencyKey": "%s",
          "kituExamineeDetails": %s
        }
        """
            .formatted(OPPIJANUMERO, idempotencyKey, idempotencyKey, kituExamineeDetails);
    performAuthorizedPostRequest(body).andExpect(status().isOk());
  }

  @Test
  public void createTiedoteFailsWhenFieldsAreMissing() throws Exception {
    performAuthorizedPostRequest(
            """
                   { "oppijanumero": "1.2.246.562.99.12345678901" }
                   """)
        .andExpect(status().isBadRequest());

    performAuthorizedPostRequest(
            """
                   { "idempotencyKey": "some-key" }
                   """)
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createTiedoteWithSameIdempotencyKeyReturnsSameId() throws Exception {
    clearDatabase();
    String idempotencyKey = UUID.randomUUID().toString();
    var json = tiedoteJson(idempotencyKey, "fi");
    var firstId = postTiedoteAndReturnId(json);
    var secondId = postTiedoteAndReturnId(json);

    assertEquals(firstId, secondId);

    List<Tiedote> tiedotteet = tiedoteRepository.findAll();
    assertEquals(1, tiedotteet.size());
    assertEquals(idempotencyKey, tiedotteet.get(0).getIdempotencyKey());
  }

  @Test
  public void createTiedoteWithDifferentIdempotencyKeysCreatesDifferentRecords() throws Exception {
    clearDatabase();

    var firstId = postTiedoteAndReturnId(tiedoteJson(UUID.randomUUID().toString(), "fi"));
    var secondId = postTiedoteAndReturnId(tiedoteJson(UUID.randomUUID().toString(), "fi"));

    assertEquals(false, firstId.equals(secondId));

    List<Tiedote> tiedotteet = tiedoteRepository.findAll();
    assertEquals(2, tiedotteet.size());
  }

  @Test
  public void getTiedoteReturns404ForUnknownId() throws Exception {
    mockMvc
        .perform(
            get("/omat-viestit/api/v1/tiedote/" + UUID.randomUUID())
                .with(tokenFor(KIELITUTKINNOSTA_TIEDOTTAJA)))
        .andExpect(status().isNotFound());
  }

  @Test
  public void getTiedoteRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/omat-viestit/api/v1/tiedote/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void createTiedoteWithoutKituExamineeDetailsReturnsBadRequest() throws Exception {
    var body = createSimpleBody();
    performAuthorizedPostRequest(body).andExpect(status().isBadRequest());
  }

  @Test
  public void createTiedoteWithEmptyKituExamineeDetailsReturnsBadRequest() throws Exception {
    var kituExamineeDetails = "{}";
    var body = createBodyWithKituExamineeDetails(kituExamineeDetails);
    performAuthorizedPostRequest(body).andExpect(status().isBadRequest());
  }

  @Test
  public void createTiedoteWithEmptyTodistuskieliReturnsBadRequest() throws Exception {
    var kituExamineeDetails =
        """
            {
              "todistuskieli": null
            }
            """;
    var body = createBodyWithKituExamineeDetails(kituExamineeDetails);
    performAuthorizedPostRequest(body).andExpect(status().isBadRequest());
  }

  @Test
  public void createTiedoteWithEmptyTodistuskieliKoodiarvoReturnsBadRequest() throws Exception {
    var validKoodistoUri = "kieli";

    var nullTodistuskieliKoodiarvo =
        objectMapper.writeValueAsString(new KituKoodiarvoDto(null, validKoodistoUri));
    var nullTodistuskieliKoodiarvoBody =
        createBodyWithKituExamineeDetails(nullTodistuskieliKoodiarvo);
    performAuthorizedPostRequest(nullTodistuskieliKoodiarvoBody).andExpect(status().isBadRequest());

    var emptyStringTodistuskieliKoodiarvo =
        objectMapper.writeValueAsString(new KituKoodiarvoDto("", validKoodistoUri));
    var emptyStringTodistuskieliKoodiarvoBody =
        createBodyWithKituExamineeDetails(emptyStringTodistuskieliKoodiarvo);
    performAuthorizedPostRequest(emptyStringTodistuskieliKoodiarvoBody)
        .andExpect(status().isBadRequest());

    var trimmedEmptyStringTodistuskieliKoodiarvo =
        objectMapper.writeValueAsString(new KituKoodiarvoDto("     ", validKoodistoUri));
    var trimmedEmptyStringTodistuskieliKoodiarvoBody =
        createBodyWithKituExamineeDetails(trimmedEmptyStringTodistuskieliKoodiarvo);
    performAuthorizedPostRequest(trimmedEmptyStringTodistuskieliKoodiarvoBody)
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createTiedoteWithEmptyTodistuskieliKoodistoUriReturnsBadRequest() throws Exception {
    var validKoodiarvo = "fi";

    var nullKoodistoUri =
        objectMapper.writeValueAsString(new KituKoodiarvoDto(validKoodiarvo, null));
    var nullKoodistoUriBody = createBodyWithKituExamineeDetails(nullKoodistoUri);
    performAuthorizedPostRequest(nullKoodistoUriBody).andExpect(status().isBadRequest());

    var emptyStringKoodistoUri =
        objectMapper.writeValueAsString(new KituKoodiarvoDto(validKoodiarvo, ""));
    var emptyStringKoodistoUriBody = createBodyWithKituExamineeDetails(emptyStringKoodistoUri);
    performAuthorizedPostRequest(emptyStringKoodistoUriBody).andExpect(status().isBadRequest());

    var trimmableEmptyStringKoodistoUri =
        objectMapper.writeValueAsString(new KituKoodiarvoDto(validKoodiarvo, "    "));
    var trimmableEmptyStringKoodistoUriBody =
        createBodyWithKituExamineeDetails(trimmableEmptyStringKoodistoUri);
    performAuthorizedPostRequest(trimmableEmptyStringKoodistoUriBody)
        .andExpect(status().isBadRequest());
  }

  @Test
  public void createTiedoteSucceedsWithOnlyTodistuskieli() throws Exception {
    var kituExamineeDetails =
        KituExamineeDetailsDto.builder().todistuskieli(new KituKoodiarvoDto("FI", "kieli")).build();
    var kituExamineeDetailsJson = objectMapper.writeValueAsString(kituExamineeDetails);
    var body = createBodyWithKituExamineeDetails(kituExamineeDetailsJson);
    performAuthorizedPostRequest(body).andExpect(status().isOk());
  }

  private String createSimpleBody() {
    var idempotencyKey = UUID.randomUUID().toString();
    var body =
        """
            {
              "oppijanumero": "%s",
              "opiskeluoikeusOid": "%s",
              "todistusBucket": "bucket",
              "todistusKey": "%s/todistus.pdf",
              "idempotencyKey": "%s"
            }
            """
            .formatted(OPPIJANUMERO, OPISKELUOIKEUS_OID, idempotencyKey, idempotencyKey);
    return body;
  }

  private String createBodyWithKituExamineeDetails(String kituExamineeDetailsJson) {
    var idempotencyKey = UUID.randomUUID().toString();
    var body =
        """
            {
              "oppijanumero": "%s",
              "opiskeluoikeusOid": "%s",
              "todistusBucket": "bucket",
              "todistusKey": "%s/todistus.pdf",
              "idempotencyKey": "%s",
              "kituExamineeDetails": %s
            }
            """
            .formatted(
                OPPIJANUMERO,
                OPISKELUOIKEUS_OID,
                idempotencyKey,
                idempotencyKey,
                kituExamineeDetailsJson);
    return body;
  }

  private @NonNull UUID postTiedoteAndReturnId(String json) throws Exception {
    var response =
        performAuthorizedPostRequest(json)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private String kituExamineeDetailsJson(String language) {
    return """
            {
              "sukunimi": "Testiläinen",
              "etunimet": "Testi Testaaja",
              "katuosoite": "Testikatu 11 C 1",
              "postinumero": "00100",
              "postitoimipaikka": "Helsinki",
              "maa": {
                "koodiarvo": "FIN",
                "koodistoUri": "maatjavaltiot1"
              },
              "email": "testi.testilainen@testikoulu.fi",
              "todistuskieli": {
                "koodiarvo": "%s",
                "koodistoUri": "kieli"
              }
            }
            """
        .formatted(language);
  }

  private String tiedoteJson(String idempotencyKey, String language) {
    var kituExamineeDetails = kituExamineeDetailsJson(language);
    return """
        {
          "oppijanumero": "%s",
          "opiskeluoikeusOid": "%s",
          "todistusBucket": "bucket",
          "todistusKey": "%s/todistus.pdf",
          "idempotencyKey": "%s",
          "kituExamineeDetails": %s
        }
        """
        .formatted(
            OPPIJANUMERO, OPISKELUOIKEUS_OID, idempotencyKey, idempotencyKey, kituExamineeDetails);
  }

  private @NonNull ResultActions performAuthorizedPostRequest(String content) throws Exception {
    return mockMvc.perform(createAuthorizedPostRequest(content));
  }

  private @NonNull MockHttpServletRequestBuilder createAuthorizedPostRequest(String content) {
    return post("/omat-viestit/api/v1/tiedote/kielitutkintotodistus")
        .with(tokenFor(KIELITUTKINNOSTA_TIEDOTTAJA))
        .contentType(MediaType.APPLICATION_JSON)
        .content(content);
  }
}

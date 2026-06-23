package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class ApiControllerTest extends TiedotuspalveluApiTest {

  private static final String OPPIJANUMERO = OidGenerator.generateHenkiloOid();
  private static final String OPISKELUOIKEUS_OID = OidGenerator.generateOpiskeluoikeusOid();

  @BeforeEach
  public void setup() {
    clearDatabase();
    super.seedHenkiloFixture();
    seedHenkilo(OPPIJANUMERO);
  }

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
        .andExpect(jsonPath("$.meta.type").value(Tiedote.TYPE_KIELITUTKINTOTODISTUS))
        .andExpect(jsonPath("$.meta.state").value(Tiedote.STATE_TIEDOTTEEN_JA_OPPIJAN_VALIDOINTI))
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
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("idempotencyKey", "must not be blank"))
        .andExpect(validationError("kituExamineeDetails", "must not be null"));

    performAuthorizedPostRequest(
            """
          { "idempotencyKey": "some-key" }
          """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("oppijanumero", "must not be blank"))
        .andExpect(validationError("kituExamineeDetails", "must not be null"));
  }

  @Test
  public void createTiedoteWithMalformedJsonReturnsErrorResponse() throws Exception {
    performAuthorizedPostRequest("{ \"oppijanumero\": ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Malformed request body"))
        .andExpect(jsonPath("$.validationErrors").isArray())
        .andExpect(jsonPath("$.validationErrors").isEmpty());
  }

  @Test
  public void createTiedoteWithUnknownOppijanumeroReturnsValidationError() throws Exception {
    var idempotencyKey = UUID.randomUUID().toString();
    var unknownOppijanumero = OidGenerator.generateHenkiloOid();
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
                unknownOppijanumero,
                OPISKELUOIKEUS_OID,
                idempotencyKey,
                idempotencyKey,
                kituExamineeDetailsJson("fi"));
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("oppijanumero", "Tuntematon oppijanumero"));
  }

  @Test
  public void createTiedoteWithSameIdempotencyKeyReturnsSameId() throws Exception {
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
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails", "must not be null"));
  }

  @Test
  public void createTiedoteWithEmptyKituExamineeDetailsReturnsBadRequest() throws Exception {
    var kituExamineeDetails = "{}";
    var body = createBodyWithKituExamineeDetails(kituExamineeDetails);
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.katuosoite", "must not be blank"))
        .andExpect(validationError("kituExamineeDetails.postinumero", "must not be blank"))
        .andExpect(validationError("kituExamineeDetails.postitoimipaikka", "must not be blank"))
        .andExpect(validationError("kituExamineeDetails.maa", "must not be null"))
        .andExpect(validationError("kituExamineeDetails.todistuskieli", "must not be null"));
  }

  @Test
  public void createTiedoteSucceedsWithOnlyTodistuskieliMaaKatuosoitePostinumeroPostitoimipaikka()
      throws Exception {
    var body = createBodyWithKituExamineeDetails(validKituExamineeDetailsJson());
    var id = postTiedoteAndReturnId(body);
    var tiedote = tiedoteRepository.findById(id);
    assertThat(tiedote).isNotEmpty();
    assertThat(tiedote.get().getTodistuskieli()).isEqualTo("FI");
    assertThat(tiedote.get().getTodistuskieliKoodistoUri()).isEqualTo("kieli");
    assertThat(tiedote.get().getKituKatuosoite()).isEqualTo("Testikatu 11 C 1");
    assertThat(tiedote.get().getMaakoodi()).isEqualTo("FIN");
    assertThat(tiedote.get().getMaaKoodistoUri()).isEqualTo("maatjavaltiot1");
  }

  @Test
  public void createTiedoteWithNullTodistuskieliReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
      {
        "katuosoite": "Testikatu 1 A 2",
        "postinumero": "00100",
        "postitoimipaikka": "HELSINKI",
        "maa": {
          "koodiarvo": "FIN",
          "koodistoUri": "maatjavaltiot1"
        },
        "todistuskieli": null
      }
    """;
    var body = createBodyWithKituExamineeDetails(kituExamineeDetailsJson);
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.todistuskieli", "must not be null"));
  }

  @Test
  public void createTiedoteWithEmptyTodistuskieliReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
          {
            "katuosoite": "Testikatu 1 A 2",
            "postinumero": "00100",
            "postitoimipaikka": "HELSINKI",
            "maa": {
              "koodiarvo": "FIN",
              "koodistoUri": "maatjavaltiot1"
            },
            "todistuskieli": {}
          }
        """;
    var body = createBodyWithKituExamineeDetails(kituExamineeDetailsJson);
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodiarvo", "must not be blank"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodistoUri", "must not be blank"));
  }

  @Test
  public void createTiedoteWithEmptyTodistuskieliKoodiarvoReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
    {
      "katuosoite": "Testikatu 1 A 2",
      "postinumero": "00100",
      "postitoimipaikka": "HELSINKI",
      "maa": {
        "koodiarvo": "FIN",
        "koodistoUri": "maatjavaltiot1"
      },
      "todistuskieli": {
        "koodiarvo": %s,
        "koodistoUri": "kieli"
      }
    }
    """;

    var nullTodistuskieliKoodiarvoBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("null"));
    performAuthorizedPostRequest(nullTodistuskieliKoodiarvoBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodiarvo", "must not be blank"));

    var emptyTodistuskieliKoodiarvoBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"\""));
    performAuthorizedPostRequest(emptyTodistuskieliKoodiarvoBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodiarvo", "must not be blank"));

    var trimmedEmptyTodistuskieliKoodiarvoBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"   \""));
    performAuthorizedPostRequest(trimmedEmptyTodistuskieliKoodiarvoBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodiarvo", "must not be blank"));
  }

  @Test
  public void createTiedoteWithEmptyTodistuskieliKoodistoUriReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
    {
      "katuosoite": "Testikatu 1 A 2",
      "postinumero": "00100",
      "postitoimipaikka": "HELSINKI",
      "maa": {
        "koodiarvo": "FIN",
        "koodistoUri": "maatjavaltiot1"
      },
      "todistuskieli": {
        "koodiarvo": "FI",
        "koodistoUri": %s
      }
    }
    """;

    var nullKoodistoUriBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("null"));
    performAuthorizedPostRequest(nullKoodistoUriBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodistoUri", "must not be blank"));

    var emptyKoodistoUriBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"\""));
    performAuthorizedPostRequest(emptyKoodistoUriBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodistoUri", "must not be blank"));

    var trimmableEmptyKoodistoUriBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"     \""));
    performAuthorizedPostRequest(trimmableEmptyKoodistoUriBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(
            validationError("kituExamineeDetails.todistuskieli.koodistoUri", "must not be blank"));
  }

  @Test
  public void createTiedoteFailsWithoutKatuosoite() throws Exception {
    var kituExamineeDetailsJson =
        """
    {
      %s
      "postinumero": "00100",
      "postitoimipaikka": "HELSINKI",
      "maa": {
        "koodiarvo": "FIN",
        "koodistoUri": "maatjavaltiot1"
      },
      "todistuskieli": {
        "koodiarvo": "FI",
        "koodistoUri": "kieli"
      }
    }
    """;

    var noKatuosoiteBody = createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted(""));
    performAuthorizedPostRequest(noKatuosoiteBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.katuosoite", "must not be blank"));

    var nullKatuosoiteBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"katuosoite\": null,"));
    performAuthorizedPostRequest(nullKatuosoiteBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.katuosoite", "must not be blank"));

    var emptyKatuosoiteBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"katuosoite\": \"\","));
    performAuthorizedPostRequest(emptyKatuosoiteBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.katuosoite", "must not be blank"));

    var emptyTrimmableKatuosoiteBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"katuosoite\": \"    \","));
    performAuthorizedPostRequest(emptyTrimmableKatuosoiteBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.katuosoite", "must not be blank"));
  }

  @Test
  public void createTiedoteFailsWithoutPostinumero() throws Exception {
    var kituExamineeDetailsJson =
        """
        {
          "katuosoite": "Testikatu 1 A 2",
          %s
          "postitoimipaikka": "HELSINKI",
          "maa": {
            "koodiarvo": "FIN",
            "koodistoUri": "maatjavaltiot1"
          },
          "todistuskieli": {
            "koodiarvo": "FI",
            "koodistoUri": "kieli"
          }
        }
        """;

    var noPostinumeroBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted(""));
    performAuthorizedPostRequest(noPostinumeroBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postinumero", "must not be blank"));

    var nullPostinumeroBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"postinumero\": null,"));
    performAuthorizedPostRequest(nullPostinumeroBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postinumero", "must not be blank"));

    var emptyPostinumeroBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"postinumero\": \"\","));
    performAuthorizedPostRequest(emptyPostinumeroBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postinumero", "must not be blank"));

    var emptyTrimmablePostinumeroBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"postinumero\": \"    \","));
    performAuthorizedPostRequest(emptyTrimmablePostinumeroBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postinumero", "must not be blank"));
  }

  @Test
  public void createTiedoteFailsWithoutPostitoimipaikka() throws Exception {
    var kituExamineeDetailsJson =
        """
            {
              "katuosoite": "Testikatu 1 A 2",
              "postinumero": "00100",
              %s
              "maa": {
                "koodiarvo": "FIN",
                "koodistoUri": "maatjavaltiot1"
              },
              "todistuskieli": {
                "koodiarvo": "FI",
                "koodistoUri": "kieli"
              }
            }
            """;

    var noPostitoimipaikkaBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted(""));
    performAuthorizedPostRequest(noPostitoimipaikkaBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postitoimipaikka", "must not be blank"));

    var nullPostitoimipaikkaBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"postitoimipaikka\": null,"));
    performAuthorizedPostRequest(nullPostitoimipaikkaBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postitoimipaikka", "must not be blank"));

    var emptyPostitoimipaikkaBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"postitoimipaikka\": \"\","));
    performAuthorizedPostRequest(emptyPostitoimipaikkaBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postitoimipaikka", "must not be blank"));

    var emptyTrimmablePostitoimipaikkaBody =
        createBodyWithKituExamineeDetails(
            kituExamineeDetailsJson.formatted("\"postitoimipaikka\": \"    \","));
    performAuthorizedPostRequest(emptyTrimmablePostitoimipaikkaBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.postitoimipaikka", "must not be blank"));
  }

  @Test
  public void createTiedoteWithNullMaaReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
          {
            "katuosoite": "Testikatu 1 A 2",
            "postinumero": "00100",
            "postitoimipaikka": "HELSINKI",
            "maa": null,
            "todistuskieli": {
              "koodiarvo": "FI",
              "koodistoUri": "kieli"
            }
          }
        """;
    var body = createBodyWithKituExamineeDetails(kituExamineeDetailsJson);
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa", "must not be null"));
  }

  @Test
  public void createTiedoteWithEmptyMaaReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
              {
                "katuosoite": "Testikatu 1 A 2",
                "postinumero": "00100",
                "postitoimipaikka": "HELSINKI",
                "maa": {},
                "todistuskieli": {
                  "koodiarvo": "FI",
                  "koodistoUri": "kieli"
                }
              }
            """;
    var body = createBodyWithKituExamineeDetails(kituExamineeDetailsJson);
    performAuthorizedPostRequest(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodiarvo", "must not be blank"))
        .andExpect(validationError("kituExamineeDetails.maa.koodistoUri", "must not be blank"));
  }

  @Test
  public void createTiedoteWithEmptyMaaKoodiarvoReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
        {
          "katuosoite": "Testikatu 1 A 2",
          "postinumero": "00100",
          "postitoimipaikka": "HELSINKI",
          "maa": {
            "koodiarvo": %s,
            "koodistoUri": "maatjavaltiot1"
          },
          "todistuskieli": {
            "koodiarvo": "FI",
            "koodistoUri": "kieli"
          }
        }
        """;

    var nullKoodiarvoBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("null"));
    performAuthorizedPostRequest(nullKoodiarvoBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodiarvo", "must not be blank"));

    var emptyKoodiarvoBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"\""));
    performAuthorizedPostRequest(emptyKoodiarvoBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodiarvo", "must not be blank"));

    var trimmableEmptyKoodiarvoBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"     \""));
    performAuthorizedPostRequest(trimmableEmptyKoodiarvoBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodiarvo", "must not be blank"));
  }

  @Test
  public void createTiedoteWithEmptyMaaKoodistoUriReturnsBadRequest() throws Exception {
    var kituExamineeDetailsJson =
        """
            {
              "katuosoite": "Testikatu 1 A 2",
              "postinumero": "00100",
              "postitoimipaikka": "HELSINKI",
              "maa": {
                "koodiarvo": "FIN",
                "koodistoUri": %s
              },
              "todistuskieli": {
                "koodiarvo": "FI",
                "koodistoUri": "kieli"
              }
            }
            """;

    var nullKoodistoUriBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("null"));
    performAuthorizedPostRequest(nullKoodistoUriBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodistoUri", "must not be blank"));

    var emptyKoodistoUriBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"\""));
    performAuthorizedPostRequest(emptyKoodistoUriBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodistoUri", "must not be blank"));

    var trimmableEmptyKoodistoUriBody =
        createBodyWithKituExamineeDetails(kituExamineeDetailsJson.formatted("\"     \""));
    performAuthorizedPostRequest(trimmableEmptyKoodistoUriBody)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.reason").value("Validation failed"))
        .andExpect(validationError("kituExamineeDetails.maa.koodistoUri", "must not be blank"));
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
              "katuosoite": "Testikatu 11 C 1",
              "postinumero": "00100",
              "postitoimipaikka": "HELSINKI",
              "maa": {
                "koodiarvo": "FIN",
                "koodistoUri": "maatjavaltiot1"
              },
              "todistuskieli": {
                "koodiarvo": "%s",
                "koodistoUri": "kieli"
              }
            }
            """
        .formatted(language);
  }

  private String validKituExamineeDetailsJson() {
    return kituExamineeDetailsJson("FI");
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

  private ResultMatcher validationError(String field, String error) {
    return jsonPath(
            "$.validationErrors[?(@.field == '%s' && @.error == '%s')]".formatted(field, error))
        .exists();
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

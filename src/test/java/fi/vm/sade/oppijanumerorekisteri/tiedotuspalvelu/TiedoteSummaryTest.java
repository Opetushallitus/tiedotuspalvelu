package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.security.CasVirkailijaUserDetailsService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class TiedoteSummaryTest extends TiedotuspalveluApiTest {
  @Test
  void summaryEndpointRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/tiedotuspalvelu/ui/tiedotteet/summary")).andExpect(status().isFound());
  }

  @Test
  void summaryEndpointReturnsForbiddenWithoutProperRole() throws Exception {
    mockMvc
        .perform(get("/tiedotuspalvelu/ui/tiedotteet/summary").with(user(VIRKAILIJA_WITHOUT_ROLES)))
        .andExpect(status().isForbidden());
  }

  @Test
  void summaryEndpointReturnsPerStateRetryCounts() throws Exception {
    insertTiedote(Tiedote.STATE_OPPIJAN_VALIDOINTI, 0);
    insertTiedote(Tiedote.STATE_OPPIJAN_VALIDOINTI, 1);
    insertTiedote(Tiedote.STATE_OPPIJAN_VALIDOINTI, 4);
    insertTiedote(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS, 3);
    insertTiedote(Tiedote.STATE_TIEDOTE_KÄSITELTY, 5);

    var responseBody =
        mockMvc
            .perform(
                get("/tiedotuspalvelu/ui/tiedotteet/summary")
                    .with(user(VIRKAILIJA_WITH_RAPORTOINTI_ROLE)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var summary = objectMapper.readValue(responseBody, VirkailijaUiController.TiedoteSummary.class);

    var byState =
        summary.stateCounts().stream()
            .collect(Collectors.toMap(VirkailijaUiController.StateCount::state, sc -> sc));

    assertEquals(5, summary.stateCounts().size());

    var validointi = byState.get(Tiedote.STATE_OPPIJAN_VALIDOINTI);
    assertEquals(3L, validointi.count());
    assertEquals(2L, validointi.retriedCount());
    assertEquals(1L, validointi.retriedThreeOrMore());

    var lahetys = byState.get(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS);
    assertEquals(1L, lahetys.count());
    assertEquals(1L, lahetys.retriedCount());
    assertEquals(1L, lahetys.retriedThreeOrMore());

    var nouto = byState.get(Tiedote.STATE_KIELITUTKINTOTODISTUKSEN_NOUTO);
    assertEquals(0L, nouto.count());
    assertEquals(0L, nouto.retriedCount());
    assertEquals(0L, nouto.retriedThreeOrMore());

    var kasitelty = byState.get(Tiedote.STATE_TIEDOTE_KÄSITELTY);
    assertEquals(1L, kasitelty.count());
    assertEquals(1L, kasitelty.retriedCount());
    assertEquals(1L, kasitelty.retriedThreeOrMore());
  }

  private void insertTiedote(String state, int retryCount) {
    jdbc.update(
        """
        INSERT INTO tiedote
          (id, oppijanumero, idempotency_key, tiedotetype_id, tiedotestate_id,
           opiskeluoikeus_oid, retry_count)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        OidGenerator.generateHenkiloOid(),
        UUID.randomUUID().toString(),
        Tiedote.TYPE_KIELITUTKINTOTODISTUS,
        state,
        OidGenerator.generateOpiskeluoikeusOid(),
        retryCount);
  }

  @BeforeEach
  void setup() {
    clearDatabase();
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
                          "ROLE_APP_TIEDOTUSPALVELU_RAPORTOINTI_1.2.246.562.10.00000000001")))
              .build();

  private final CasVirkailijaUserDetailsService.CasAuthenticatedUser VIRKAILIJA_WITHOUT_ROLES =
      CasVirkailijaUserDetailsService.CasAuthenticatedUser.builder()
          .username("emmieioikkia")
          .attributes(
              Map.of(
                  "oidHenkilo",
                  List.of("1.2.246.562.24.38583027941"),
                  "kayttajaTyyppi",
                  List.of("VIRKAILIJA"),
                  "idpEntityId",
                  List.of("usernamePassword")))
          .authorities(List.of())
          .build();
}

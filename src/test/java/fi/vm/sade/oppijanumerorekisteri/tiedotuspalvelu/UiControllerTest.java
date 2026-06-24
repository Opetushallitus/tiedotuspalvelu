package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UiControllerTest extends TiedotuspalveluApiTest {

  @BeforeEach
  public void setup() throws Exception {
    clearDatabase();
  }

  @Test
  public void returnsOnlyCurrentUsersTiedotteet() throws Exception {
    createTiedote(OPPIJANUMERO_NORDEA_DEMO);
    createTiedote(OPPIJANUMERO_NORDEA_DEMO);
    createTiedote("1.2.246.562.24.66666666666");

    var response =
        mockMvc
            .perform(get("/omat-viestit/ui/tiedotteet").with(user(OPPIJA_NORDEA_DEMO)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UiController.TiedoteDto[] tiedotteet =
        objectMapper.readValue(response, UiController.TiedoteDto[].class);

    assertEquals(2, tiedotteet.length);

    for (UiController.TiedoteDto tiedote : tiedotteet) {
      assertNotNull(tiedote.id());
      assertFalse(tiedote.id().toString().isEmpty());
    }
  }

  @Test
  public void returnsTiedotteetInOrderFromNewestToOldest() throws Exception {
    var t1 = createTiedote(OPPIJANUMERO_NORDEA_DEMO);
    var t2 = createTiedote(OPPIJANUMERO_NORDEA_DEMO);
    var t3 = createTiedote(OPPIJANUMERO_NORDEA_DEMO);

    var response =
        mockMvc
            .perform(get("/omat-viestit/ui/tiedotteet").with(user(OPPIJA_NORDEA_DEMO)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UiController.TiedoteDto[] tiedotteet =
        objectMapper.readValue(response, UiController.TiedoteDto[].class);

    assertEquals(3, tiedotteet.length);
    assertEquals(t3.getId(), tiedotteet[0].id());
    assertEquals(t2.getId(), tiedotteet[1].id());
    assertEquals(t1.getId(), tiedotteet[2].id());
  }

  @Test
  public void returnsMeForNormalSuomiFi() throws Exception {
    var response =
        mockMvc
            .perform(get("/omat-viestit/ui/me").with(user(OPPIJA_NORDEA_DEMO)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UiController.MeResponse me = objectMapper.readValue(response, UiController.MeResponse.class);
    assertEquals("Nordea Demo", me.nimi());
  }

  @Test
  public void returnsMeFor() throws Exception {
    var response =
        mockMvc
            .perform(get("/omat-viestit/ui/me").with(user(SUOMIFI_TESTIHENKILO)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UiController.MeResponse me = objectMapper.readValue(response, UiController.MeResponse.class);
    assertEquals("Testihenkilö", me.nimi());
  }

  @Test
  public void returnsMeForEidas() throws Exception {
    var response =
        mockMvc
            .perform(get("/omat-viestit/ui/me").with(user(EIDAS_HENKILO)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UiController.MeResponse me = objectMapper.readValue(response, UiController.MeResponse.class);
    assertEquals("Leon Elias Germany", me.nimi());
  }
}

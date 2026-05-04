package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.assumptions;

import static org.assertj.core.api.Assertions.assertThat;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CasVirkailijaAssumptionsTest extends CasAssumptionsTestBase {

  @Autowired TiedotuspalveluProperties properties;

  @Test
  void assumeCasVirkailijaReturnsKäyttäjätunnusAsUsername() throws Exception {
    var assertion = authenticateAndValidate("riinaraportoija", "riinaraportoija");
    assertThat(assertion.getPrincipal().getName()).isEqualTo("riinaraportoija");
  }

  @Test
  void assumeCasVirkailijaReturnsHenkiloOid() throws Exception {
    var assertion = authenticateAndValidate("riinaraportoija", "riinaraportoija");
    assertThat(assertion.getPrincipal().getAttributes().get("oidHenkilo"))
        .isEqualTo("1.2.246.562.24.80170786687");
  }

  @Test
  void assumeCasVirkailijaReturnsKayttajaTyyppi() throws Exception {
    var assertion = authenticateAndValidate("riinaraportoija", "riinaraportoija");
    assertThat(assertion.getPrincipal().getAttributes().get("kayttajaTyyppi"))
        .isEqualTo("VIRKAILIJA");
  }

  @Test
  void assumeCasVirkailijaReturnsIdpEntityId() throws Exception {
    var assertion = authenticateAndValidate("riinaraportoija", "riinaraportoija");
    assertThat(assertion.getPrincipal().getAttributes().get("idpEntityId"))
        .isEqualTo("usernamePassword");
  }

  @Test
  void assumeCasVirkailijaReturnsKnownFields() throws Exception {
    var assertion = authenticateAndValidate("riinaraportoija", "riinaraportoija");
    assertThat(assertion.getPrincipal().getAttributes().keySet())
        .containsExactlyInAnyOrder("oidHenkilo", "kayttajaTyyppi", "idpEntityId", "roles");
  }

  @Test
  void assumeCasVirkailijaReturnsRoles() throws Exception {
    var assertion = authenticateAndValidate("riinaraportoija", "riinaraportoija");
    assertThat((List<String>) assertion.getPrincipal().getAttributes().get("roles"))
        .containsExactlyInAnyOrder(
            "ROLE_APP_TIEDOTUSPALVELU_RAPORTOINTI",
            "ROLE_APP_TIEDOTUSPALVELU_RAPORTOINTI_1.2.246.562.10.00000000001");
  }

  @Override
  String casServerUrl() {
    return properties.casVirkailija().serverUrl();
  }

  @Override
  String serviceBaseUrl() {
    return properties.casVirkailija().serviceBaseUrl();
  }
}

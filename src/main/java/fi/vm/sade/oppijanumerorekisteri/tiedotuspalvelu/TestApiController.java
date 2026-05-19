package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.koski.FetchKielitutkintotodistusTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.HenkiloTableLoader;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.ValidateTiedoteTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SendSuomiFiViestitTask;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration
@RestController
@ConditionalOnProperty(name = "tiedotuspalvelu.testapi.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TestApiController {
  private final JdbcTemplate jdbcTemplate;
  private final ValidateTiedoteTask validateTiedoteTask;
  private final SendSuomiFiViestitTask sendSuomiFiViestitTask;
  private final FetchKielitutkintotodistusTask fetchKielitutkintotodistusTask;
  private final HenkiloTableLoader henkiloTableLoader;

  @Bean
  @Order(0)
  SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher("/test/**")
        .csrf(CsrfConfigurer::disable)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  @PostMapping("/test/reset")
  public ResponseEntity<Void> reset() {
    var tables =
        List.of(
            "tiedote",
            "spring_session",
            "spring_session_attributes",
            "suomifi_viesti",
            "suomifi_viestit_event",
            "suomifi_viestit_events_cursor");
    jdbcTemplate.execute("TRUNCATE " + Strings.join(tables, ',') + " CASCADE");
    henkiloTableLoader.load("fulldump/henkilo/v1/henkilo.csv");
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test/runValidateTiedoteTask")
  public ResponseEntity<Void> runValidateTiedoteTask() {
    validateTiedoteTask.execute();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test/runSendSuomiFiViestitTask")
  public ResponseEntity<Void> runSendSuomiFiViestitTask() {
    sendSuomiFiViestitTask.execute();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test/runFetchKielitutkintotodistusTask")
  public ResponseEntity<Void> runFetchKielitutkintotodistusTask() {
    fetchKielitutkintotodistusTask.execute();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test/generateHenkiloOid")
  public OidResponse generateHenkiloOid() {
    return new OidResponse(OidGenerator.generateHenkiloOid());
  }

  @PostMapping("/test/generateOpiskeluoikeusOid")
  public OidResponse generateOpiskeluoikeusOid() {
    return new OidResponse(OidGenerator.generateOpiskeluoikeusOid());
  }

  public record OidResponse(String oid) {}
}

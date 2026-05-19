package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.security;

import fi.vm.sade.JdbcSessionMappingStorage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
public class CasClientSessionCleanerTask {

  private final JdbcSessionMappingStorage jdbcSessionMappingStorage;

  public void execute() {
    jdbcSessionMappingStorage.clean();
    log.info("Finished running CasClientSessionCleanerTask");
  }
}

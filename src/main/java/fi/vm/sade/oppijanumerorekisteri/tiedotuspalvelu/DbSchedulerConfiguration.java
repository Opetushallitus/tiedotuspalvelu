package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static fi.vm.sade.RequestIdFilter.REQUEST_ID_ATTRIBUTE;

import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import fi.vm.sade.JdbcSessionMappingStorage;
import fi.vm.sade.RequestIdFilter;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.koski.FetchKielitutkintotodistusTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.locale.FetchLocalisationsTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.FetchOppijaTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.FetchSuomiFiViestitEventsTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SendSuomiFiViestitTask;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@AllArgsConstructor
public class DbSchedulerConfiguration {
  private final FetchOppijaTask fetchOppijaTask;
  private final FetchKielitutkintotodistusTask fetchKielitutkintotodistusTask;
  private final SendSuomiFiViestitTask sendSuomiFiViestitTask;
  private final FetchSuomiFiViestitEventsTask fetchSuomiFiViestitEventsTask;
  private final FetchLocalisationsTask fetchLocalisationsTask;
  private final JdbcSessionMappingStorage jdbcSessionMappingStorage;

  @Bean
  @ConditionalOnProperty(name = "tiedotuspalvelu.fetch-oppija.enabled", havingValue = "true")
  public Task<Void> fetchOppijaTaskBean() {
    return wrapTaskWithRequestId(
        "fetch-oppija-task",
        Schedules.fixedDelay(Duration.ofSeconds(10)),
        fetchOppijaTask::execute);
  }

  @Bean
  @ConditionalOnProperty(name = "tiedotuspalvelu.suomifi-viestit.enabled", havingValue = "true")
  public Task<Void> sendSuomiFiViestitTaskBean() {
    return wrapTaskWithRequestId(
        "send-suomi-fi-viestit-task",
        Schedules.fixedDelay(Duration.ofSeconds(10)),
        sendSuomiFiViestitTask::execute);
  }

  @Bean
  @ConditionalOnProperty(
      name = "tiedotuspalvelu.fetch-kielitutkintotodistus.enabled",
      havingValue = "true")
  public Task<Void> fetchKielitutkintotodistusTaskBean() {
    return wrapTaskWithRequestId(
        "fetch-kielitutkintotoistus-task",
        Schedules.fixedDelay(Duration.ofSeconds(10)),
        fetchKielitutkintotodistusTask::execute);
  }

  @Bean
  @ConditionalOnProperty(name = "tiedotuspalvelu.suomifi-viestit.enabled", havingValue = "true")
  public Task<Void> fetchSuomiFiEventsTaskBean() {
    return wrapTaskWithRequestId(
        "fetch-suomi-fi-viestit-events-task",
        Schedules.fixedDelay(Duration.ofMinutes(1)),
        fetchSuomiFiViestitEventsTask::execute);
  }

  @Bean
  @ConditionalOnProperty(name = "tiedotuspalvelu.fetch-localisations.enabled", havingValue = "true")
  public Task<Void> fetchLocalisationsTaskBean() {
    return wrapTaskWithRequestId(
        "fetch-localisations-task",
        Schedules.fixedDelay(Duration.ofMinutes(5)),
        fetchLocalisationsTask::execute);
  }

  @Bean
  public Task<Void> casClientSessionCleanerTaskBean() {
    return wrapTaskWithRequestId(
        "cas-client-session-cleaner",
        Schedules.fixedDelay(Duration.ofHours(1)),
        () -> {
          jdbcSessionMappingStorage.clean();
          log.info("Finished running CasClientSessionCleanerTask");
        });
  }

  private Task<Void> wrapTaskWithRequestId(String name, Schedule schedule, Runnable action) {
    return Tasks.recurring(name, schedule)
        .execute(
            (inst, ctx) -> {
              try {
                var requestId = RequestIdFilter.generateRequestId();
                MDC.put(REQUEST_ID_ATTRIBUTE, requestId);
                action.run();
              } finally {
                MDC.remove(REQUEST_ID_ATTRIBUTE);
              }
            });
  }
}

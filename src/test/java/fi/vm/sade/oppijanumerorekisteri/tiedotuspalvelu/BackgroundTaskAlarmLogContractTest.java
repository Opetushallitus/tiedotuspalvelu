package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.locale.FetchLocalisationsTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.locale.LokalisointiClient;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.ValidateTiedoteTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.security.CasClientSessionCleanerTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.FetchSuomiFiViestitEventsTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SendSuomiFiViestitTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SuomiFiViestitClient;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.schema.EventsResponse;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ExtendWith(OutputCaptureExtension.class)
public class BackgroundTaskAlarmLogContractTest extends TiedotuspalveluApiTest {

  @Autowired private ValidateTiedoteTask validateTiedoteTask;
  @Autowired private SendSuomiFiViestitTask sendSuomiFiViestitTask;
  @Autowired private FetchSuomiFiViestitEventsTask fetchSuomiFiViestitEventsTask;
  @Autowired private FetchLocalisationsTask fetchLocalisationsTask;
  @Autowired private CasClientSessionCleanerTask casClientSessionCleanerTask;

  @MockitoBean private SuomiFiViestitClient suomiFiViestitClient;
  @MockitoBean private LokalisointiClient lokalisointiClient;

  @BeforeEach
  public void setup() {
    clearDatabase();
  }

  @Test
  public void validateTiedoteTaskEmitsAlarmLogLine(CapturedOutput output) {
    validateTiedoteTask.execute();
    Assertions.assertThat(output).contains("Finished running ValidateTiedoteTask");
  }

  @Test
  public void sendSuomiFiViestitTaskEmitsAlarmLogLine(CapturedOutput output) {
    sendSuomiFiViestitTask.execute();
    Assertions.assertThat(output).contains("Finished running SendSuomiFiViestitTask");
  }

  @Test
  public void fetchSuomiFiViestitEventsTaskEmitsAlarmLogLine(CapturedOutput output) {
    when(suomiFiViestitClient.fetchEvents(null))
        .thenReturn(new EventsResponse("token-1", List.of()));
    fetchSuomiFiViestitEventsTask.execute();
    Assertions.assertThat(output).contains("Finished running FetchSuomiFiViestitEventsTask");
  }

  @Test
  public void fetchLocalisationsTaskEmitsAlarmLogLine(CapturedOutput output) {
    when(lokalisointiClient.getLocalisations(anyString())).thenReturn(List.of());
    fetchLocalisationsTask.execute();
    Assertions.assertThat(output).contains("Finished running FetchLocalisationsTask");
  }

  @Test
  public void casClientSessionCleanerTaskEmitsAlarmLogLine(CapturedOutput output) {
    casClientSessionCleanerTask.execute();
    Assertions.assertThat(output).contains("Finished running CasClientSessionCleanerTask");
  }
}

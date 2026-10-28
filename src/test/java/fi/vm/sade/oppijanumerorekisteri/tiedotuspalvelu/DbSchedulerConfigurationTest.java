package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import com.github.kagkarlsson.scheduler.task.Task;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.koski.FetchKielitutkintotodistusTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.locale.FetchLocalisationsTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.HenkiloImportTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.ValidateTiedoteTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.security.CasClientSessionCleanerTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.FetchSuomiFiViestitEventsTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SendSuomiFiViestitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static fi.vm.sade.RequestIdFilter.REQUEST_ID_ATTRIBUTE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DbSchedulerConfigurationTest {

    @Mock private ValidateTiedoteTask validateTiedoteTask;
    @Mock private FetchKielitutkintotodistusTask fetchKielitutkintotodistusTask;
    @Mock private SendSuomiFiViestitTask sendSuomiFiViestitTask;
    @Mock private FetchSuomiFiViestitEventsTask fetchSuomiFiViestitEventsTask;
    @Mock private FetchLocalisationsTask fetchLocalisationsTask;
    @Mock private CasClientSessionCleanerTask casClientSessionCleanerTask;
    @Mock private HenkiloImportTask henkiloImportTask;

    @Test
    void taskBeansUseExpectedNames() {
        var config = new DbSchedulerConfiguration(
            validateTiedoteTask,
            fetchKielitutkintotodistusTask,
            sendSuomiFiViestitTask,
            fetchSuomiFiViestitEventsTask,
            fetchLocalisationsTask,
            casClientSessionCleanerTask);

        assertTaskName(config.fetchOppijaTaskBean(), "fetch-oppija-task");
        assertTaskName(config.sendSuomiFiViestitTaskBean(), "send-suomi-fi-viestit-task");
        assertTaskName(config.fetchKielitutkintotodistusTaskBean(), "fetch-kielitutkintotoistus-task");
        assertTaskName(config.fetchSuomiFiEventsTaskBean(), "fetch-suomi-fi-viestit-events-task");
        assertTaskName(config.fetchLocalisationsTaskBean(), "fetch-localisations-task");
        assertTaskName(config.henkiloImportTaskBean(henkiloImportTask), "henkilo-import-task");
        assertTaskName(config.casClientSessionCleanerTaskBean(), "cas-client-session-cleaner");
    }

    @Test
    void taskExecutionAddsAndRemovesRequestIdInMdc() {
        var config = new DbSchedulerConfiguration(
            validateTiedoteTask,
            fetchKielitutkintotodistusTask,
            sendSuomiFiViestitTask,
            fetchSuomiFiViestitEventsTask,
            fetchLocalisationsTask,
            casClientSessionCleanerTask);

        doAnswer(
                invocation -> {
                    var requestId = MDC.get(REQUEST_ID_ATTRIBUTE);
                    assertNotNull(requestId);
                    assertEquals(requestId, MDC.get(REQUEST_ID_ATTRIBUTE));
                    return null;
                })
            .when(validateTiedoteTask)
            .execute();

        Task<Void> task = config.fetchOppijaTaskBean();
        task.execute(task.instance("test-instance"), null);

        assertNull(MDC.get(REQUEST_ID_ATTRIBUTE));
        verify(validateTiedoteTask).execute();
    }

    private void assertTaskName(Task<Void> task, String expectedName) {
        assertNotNull(task);
        assertEquals(expectedName, task.getName());
    }
}

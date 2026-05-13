package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.koski;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.Tiedote;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluApiTest;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SuomiFiViesti;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FetchKielitutkintotodistusTaskTest extends TiedotuspalveluApiTest {

  @Autowired private FetchKielitutkintotodistusTask fetchKielitutkintotodistusTask;

  private static String OPPIJANUMERO_HELLIN_SEVILLANTES = "1.2.246.562.98.19783284870";

  @Test
  public void successfullyFetchesKielitutkintotodistusAndUpdatesState() throws Exception {
    var tiedote =
        Tiedote.builder()
            .idempotencyKey(UUID.randomUUID().toString())
            .retryCount(0)
            .state(Tiedote.STATE_KIELITUTKINTOTODISTUKSEN_NOUTO)
            .type(Tiedote.TYPE_KIELITUTKINTOTODISTUS)
            .oppijanumero(OPPIJANUMERO_HELLIN_SEVILLANTES)
            .todistusBucketName("bucket")
            .todistusObjectKey("todistusKey")
            .opiskeluoikeusOid(UUID.randomUUID().toString())
            .kituKatuosoite("katuosoite")
            .kituPostinumero("00100")
            .kituPostitoimipaikka("Helsinki")
            .maakoodi("FIN")
            .maaKoodistoUri("maatjavaltiot1")
            .todistuskieli("FI")
            .todistuskieliKoodistoUri("kieli")
            .created(OffsetDateTime.now())
            .updated(OffsetDateTime.now())
            .processedAt(OffsetDateTime.now())
            .build();

    var suomiFiViesti =
        SuomiFiViesti.builder()
            .name("Nimi1 Nimi2 Sukunimi")
            .henkilotunnus("01011970-0001")
            .messageType(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL)
            .tiedote(tiedote)
            .countryCode("FI")
            .city("Helsinki")
            .streetAddress("katuosoite")
            .zipCode("00100")
            .created(OffsetDateTime.now())
            .updated(OffsetDateTime.now())
            .processedAt(OffsetDateTime.now())
            .otsikko("otsikko")
            .sisalto("sisalto")
            .messageId("messageId")
            .build();

    tiedote.setViesti(suomiFiViesti);

    var persisted = tiedoteRepository.save(tiedote);

    fetchKielitutkintotodistusTask.execute();

    var after = tiedoteRepository.findById(persisted.getId()).orElseThrow();
    assertThat(after.getState())
        .isEqualTo(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS_PAPERIPOSTIOPTIOLLA);
    assertThat(after.getKielitutkintotodistusPdf()).isNotNull();
  }
}

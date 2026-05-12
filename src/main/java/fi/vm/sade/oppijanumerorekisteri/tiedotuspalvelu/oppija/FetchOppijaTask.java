package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.Tiedote;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedoteProcessingTask;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedoteRepository;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.CountryCodeConverter;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit.SuomiFiViesti;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class FetchOppijaTask extends TiedoteProcessingTask {

  private final OppijanumerorekisteriClient oppijanumerorekisteriClient;

  public FetchOppijaTask(
      TiedoteRepository tiedoteRepository,
      OppijanumerorekisteriClient oppijanumerorekisteriClient,
      TransactionTemplate transactionTemplate) {
    super(transactionTemplate, tiedoteRepository);
    this.oppijanumerorekisteriClient = oppijanumerorekisteriClient;
  }

  @Override
  protected List<String> statesToProcess() {
    return List.of(Tiedote.STATE_OPPIJAN_VALIDOINTI);
  }

  @Override
  protected void processTiedote(Tiedote tiedote) {
    var oppija = oppijanumerorekisteriClient.getOppija(tiedote.getOppijanumero());
    tiedote.setViesti(createSuomiFiViesti(tiedote, oppija));
    tiedote.setProcessedAt(OffsetDateTime.now());
    tiedote.setState(Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS);
  }

  private SuomiFiViesti createSuomiFiViesti(Tiedote tiedote, Oppija oppija) {
    var suomiFiViestiBuilder =
        SuomiFiViesti.builder()
            .tiedote(tiedote)
            .henkilotunnus(oppija.hetu())
            .name(oppija.etunimet() + " " + oppija.sukunimi())
            .messageType(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_ELECTRONIC);
    setPostalInfoFromTiedote(suomiFiViestiBuilder, tiedote);
    return suomiFiViestiBuilder.build();
  }

  private void setPostalInfoFromTiedote(
      SuomiFiViesti.SuomiFiViestiBuilder suomiFiViestiBuilder, Tiedote tiedote) {
    if (tiedote.getKituKatuosoite() == null) {
      throw new IllegalArgumentException("Tiedote kitu katuosoite is null");
    }
    if (tiedote.getKituPostinumero() == null) {
      throw new IllegalArgumentException("Tiedote kitu postinumero is null");
    }
    if (tiedote.getKituPostitoimipaikka() == null) {
      throw new IllegalArgumentException("Tiedote kitu postitoimipaikka is null");
    }
    if (tiedote.getMaakoodi() == null) {
      throw new IllegalArgumentException("Tiedote maakoodi is null");
    }

    suomiFiViestiBuilder.streetAddress(tiedote.getKituKatuosoite());
    suomiFiViestiBuilder.zipCode(tiedote.getKituPostinumero());
    suomiFiViestiBuilder.city(tiedote.getKituPostitoimipaikka());
    suomiFiViestiBuilder.countryCode(CountryCodeConverter.alpha3ToAlpha2(tiedote.getMaakoodi()));
  }
}

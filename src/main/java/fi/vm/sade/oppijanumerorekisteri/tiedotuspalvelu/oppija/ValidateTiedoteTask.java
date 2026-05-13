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
public class ValidateTiedoteTask extends TiedoteProcessingTask {

  private final OppijanumerorekisteriClient oppijanumerorekisteriClient;

  public ValidateTiedoteTask(
      TiedoteRepository tiedoteRepository,
      OppijanumerorekisteriClient oppijanumerorekisteriClient,
      TransactionTemplate transactionTemplate) {
    super(transactionTemplate, tiedoteRepository);
    this.oppijanumerorekisteriClient = oppijanumerorekisteriClient;
  }

  @Override
  protected List<String> statesToProcess() {
    return List.of(Tiedote.STATE_TIEDOTTEEN_JA_OPPIJAN_VALIDOINTI);
  }

  @Override
  protected void processTiedote(Tiedote tiedote) {
    var oppija = oppijanumerorekisteriClient.getOppija(tiedote.getOppijanumero());

    var messageType =
        oppija.hetu() == null
            ? SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL
            : SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_ELECTRONIC;

    var suomiFiViestiBuilder =
        SuomiFiViesti.builder()
            .tiedote(tiedote)
            .henkilotunnus(oppija.hetu())
            .name(oppija.etunimet() + " " + oppija.sukunimi())
            .messageType(messageType);

    setPostalInfoFromTiedoteKituDetails(suomiFiViestiBuilder, tiedote);

    var suomiFiViesti = suomiFiViestiBuilder.build();
    tiedote.setViesti(suomiFiViesti);

    var nextTiedoteState =
        suomiFiViesti.getMessageType().equals(SuomiFiViesti.SUOMI_FI_VIESTI_MESSAGE_TYPE_PAPER_MAIL)
            ? Tiedote.STATE_KIELITUTKINTOTODISTUKSEN_NOUTO
            : Tiedote.STATE_SUOMIFI_VIESTIN_LÄHETYS;
    tiedote.setState(nextTiedoteState);

    tiedote.setProcessedAt(OffsetDateTime.now());
  }

  private void setPostalInfoFromTiedoteKituDetails(
      SuomiFiViesti.SuomiFiViestiBuilder builder, Tiedote tiedote) {
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

    builder
        .streetAddress(tiedote.getKituKatuosoite())
        .zipCode(tiedote.getKituPostinumero())
        .city(tiedote.getKituPostitoimipaikka())
        .countryCode(CountryCodeConverter.alpha3ToAlpha2(tiedote.getMaakoodi()));
  }
}

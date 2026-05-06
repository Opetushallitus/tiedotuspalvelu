package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.Tiedote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TiedoteDtoMapper {

  public static Tiedote toModel(TiedoteDto tiedoteDto) {
    var opiskeluoikeusOid =
        tiedoteDto.opiskeluoikeusOid() != null ? tiedoteDto.opiskeluoikeusOid() : "[uupuu]";

    return Tiedote.builder()
        .oppijanumero(tiedoteDto.oppijanumero())
        .idempotencyKey(tiedoteDto.idempotencyKey())
        .todistusBucketName(tiedoteDto.todistusBucketName())
        .todistusObjectKey(tiedoteDto.todistusObjectKey())
        .opiskeluoikeusOid(opiskeluoikeusOid)
        .type(Tiedote.TYPE_KIELITUTKINTOTODISTUS)
        .state(Tiedote.STATE_OPPIJAN_VALIDOINTI)
        .todistuskieli(tiedoteDto.kituExamineeDetails().todistuskieli().koodiarvo())
        .todistuskieliKoodistoUri(tiedoteDto.kituExamineeDetails().todistuskieli().koodistoUri())
        .maakoodi(tiedoteDto.kituExamineeDetails().maa().koodiarvo())
        .maaKoodistoUri(tiedoteDto.kituExamineeDetails().maa().koodistoUri())
        .kituKatuosoite(tiedoteDto.kituExamineeDetails().katuosoite())
        .kituPostinumero(tiedoteDto.kituExamineeDetails().postinumero())
        .kituPostitoimipaikka(tiedoteDto.kituExamineeDetails().postitoimipaikka())
        .build();
  }
}

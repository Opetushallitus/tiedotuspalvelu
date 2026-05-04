package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.util;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.Tiedote;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api.TiedoteDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TodistuskieliUtil {
  public static String getTodistuskieli(TiedoteDto tiedoteDto) {
    if (tiedoteDto.kituExamineeDetails() == null) {
      log.warn("No kituExamineeDetails in tiedoteDto");
      return null;
    }

    if (tiedoteDto.kituExamineeDetails().todistuskieli() == null) {
      log.warn("No todistuskieli found in tiedoteDto.kituExamineeDetails");
      return null;
    }

    return tiedoteDto.kituExamineeDetails().todistuskieli().koodiarvo();
  }

  public static String getTodistuskieli(Tiedote tiedote) {
    if (tiedote.getTodistuskieli() == null) {
      log.warn(
          "tiedote.todistuskieli is null, using default language (%s) instead"
              .formatted(Tiedote.DEFAULT_TODISTUSKIELI));
      return Tiedote.DEFAULT_TODISTUSKIELI;
    }

    return tiedote.getTodistuskieli();
  }
}

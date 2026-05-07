package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * For converting maatjavaltiot1 -koodisto
 * (https://virkailija.testiopintopolku.fi/koodisto-service/ui/koodisto/view/maatjavaltiot1/2)
 * values (ISO 3166-1 A-3 country codes, 3 characters) to DVV/Suomi.fi postage endpoint accepted
 * country codes (ISO 3166-1 A-2, 2 characters). You cannot directly map ISO 3166-1 A-3 country
 * codes to ISO 3166-1 A-2 country codes in Java, so we construct a Map for the conversions, using
 * Java's in-built Locale ISO codes, which hopefully cover all the maatjavaltiot1 country codes.
 */
@Component
@Slf4j
public class CountryCodeConverter {
  private static final Map<String, String> A3_TO_A2;

  static {
    Map<String, String> m = new HashMap<>();
    for (String a2 : Locale.getISOCountries()) {
      String a3 = new Locale("", a2).getISO3Country();
      if (!a3.isEmpty()) {
        m.put(a3, a2);
      }
    }
    A3_TO_A2 = Map.copyOf(m);
  }

  public static String alpha3ToAlpha2(String alpha3) {
    if (alpha3 == null) {
      log.warn("Attempted to convert a null string to a ISO 3166-1 A-2 country code");
      return null;
    }
    return A3_TO_A2.get(alpha3);
  }
}

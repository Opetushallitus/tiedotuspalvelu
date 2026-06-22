package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CountryCodeConverterTest {

  @Test
  public void convertsFINtoFI() {
    assertThat(CountryCodeConverter.alpha3ToAlpha2("FIN")).isEqualTo("FI");
  }

  @Test
  public void convertsFRAtoFR() {
    assertThat(CountryCodeConverter.alpha3ToAlpha2("FRA")).isEqualTo("FR");
  }

  @Test
  public void throwsIfAttemptingToConvertNull() {
    var exception =
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> CountryCodeConverter.alpha3ToAlpha2(null));
    assertThat(exception.getMessage()).isEqualTo("alpha3 country code cannot be null");
  }

  @ParameterizedTest
  @ValueSource(strings = {"XXX", "123", "EAX", "   "})
  public void throwsIfAttemptingToConvertNonExistentCountry(String countryCode) {
    var exception =
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> CountryCodeConverter.alpha3ToAlpha2(countryCode));
    assertThat(exception.getMessage())
        .isEqualTo(
            "alpha3 country code \"" + countryCode + "\" not found in Locale.getISOCountries()");
  }
}

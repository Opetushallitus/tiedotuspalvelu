package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.suomifiviestit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
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
  public void logsWarningIfAttemptingToConvertNull(CapturedOutput output) {
    assertThat(CountryCodeConverter.alpha3ToAlpha2(null)).isNull();
    assertThat(output)
        .containsPattern("Attempted to convert a null string to a ISO 3166-1 A-2 country code");
  }
}

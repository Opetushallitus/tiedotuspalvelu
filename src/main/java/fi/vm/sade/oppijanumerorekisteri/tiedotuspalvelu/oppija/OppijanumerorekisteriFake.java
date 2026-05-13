package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.schema.HenkiloDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fakes/oppijanumerorekisteri")
@ConditionalOnProperty(name = "tiedotuspalvelu.testapi.enabled", havingValue = "true")
public class OppijanumerorekisteriFake {

  @GetMapping("/henkilo/{henkiloOid}")
  public ResponseEntity getOppija(@PathVariable String henkiloOid) {
    if ("1.2.246.562.24.73833272757".equals(henkiloOid)) {
      return ResponseEntity.ok(
          HenkiloDto.builder()
              .oppijanumero(henkiloOid)
              .hetu("210281-9988")
              .etunimet("Nordea")
              .sukunimi("Demo")
              .build());
    } else if ("1.2.246.562.98.19783284870".equals(henkiloOid)) {
      return ResponseEntity.ok(
          HenkiloDto.builder()
              .oppijanumero(henkiloOid)
              .hetu("041157-998B")
              .etunimet("Hellin")
              .sukunimi("Sevillantes")
              .build());
    } else if ("1.2.246.562.98.77340099611".equals(henkiloOid)) {
      return ResponseEntity.ok(
          HenkiloDto.builder()
              .oppijanumero(henkiloOid)
              .hetu("181064-998C")
              .etunimet("Hennakaarina")
              .sukunimi("Sevillantes")
              .build());
    } else if ("1.2.246.562.24.59505755490".equals(henkiloOid)) {
      return ResponseEntity.ok(
          HenkiloDto.builder()
              .oppijanumero("1.2.246.562.24.59505755490")
              .hetu(null)
              .etunimet("Henkilö")
              .sukunimi("Hetuton")
              .build());
    }

    return ResponseEntity.status(418)
        .body("Olen teepannu ...eikun kutsuit tätä feikkirajapintaa väärin!");
  }
}

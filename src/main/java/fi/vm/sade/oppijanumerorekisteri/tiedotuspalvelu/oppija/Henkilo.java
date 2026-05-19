package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "henkilo")
@Getter
public class Henkilo {
  @Id private String oid;
}

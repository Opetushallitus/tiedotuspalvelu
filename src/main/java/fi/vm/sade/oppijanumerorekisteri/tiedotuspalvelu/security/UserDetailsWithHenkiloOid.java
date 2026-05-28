package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.security;

import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserDetailsWithHenkiloOid extends UserDetails {
  Optional<String> getHenkiloOid();
}

package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Oppija(@Nullable String hetu, String etunimet, String sukunimi) {}

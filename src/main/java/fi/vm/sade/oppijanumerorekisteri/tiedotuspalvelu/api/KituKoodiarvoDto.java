package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import jakarta.validation.constraints.NotBlank;

public record KituKoodiarvoDto(@NotBlank String koodiarvo, @NotBlank String koodistoUri) {}

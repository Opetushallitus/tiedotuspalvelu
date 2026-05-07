package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record KituExamineeDetailsDto(
    @Schema(example = "Meikäläinen") String sukunimi,
    @Schema(example = "Matti Matias") String etunimet,
    @Schema(example = "Testikatu 12") @NotBlank String katuosoite,
    @Schema(example = "00100") @NotBlank String postinumero,
    @Schema(example = "Helsinki") @NotBlank String postitoimipaikka,
    @Valid @NotNull KituKoodiarvoDto maa,
    @Schema(example = "matti.meikalainen@schoolemail.fi") String email,
    @Valid @NotNull KituKoodiarvoDto todistuskieli) {}

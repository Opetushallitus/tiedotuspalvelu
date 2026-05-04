package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import org.springframework.lang.Nullable;

@Builder
public record KituExamineeDetailsDto (
    @Schema(example = "Meikäläinen") String sukunimi,
    @Schema(example = "Matti Matias") String etunimet,
    @Schema(example = "Testikatu 12") String katuosoite,
    @Schema(example = "00100") String postinumero,
    @Schema(example = "Helsinki") String postitoimipaikka,
    @Nullable KituKoodiarvoDto maa,
    @Schema(example = "matti.meikalainen@schoolemail.fi") String email,
    @Nullable KituKoodiarvoDto todistuskieli
) {}



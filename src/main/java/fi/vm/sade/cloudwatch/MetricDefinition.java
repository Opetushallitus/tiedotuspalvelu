package fi.vm.sade.cloudwatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.Builder;

@Builder
public record MetricDefinition(
    @JsonProperty("Name") String name,
    @JsonProperty("Unit") Optional<String> unit,
    @JsonProperty("StorageResolution") Optional<String> storageResolution) {}

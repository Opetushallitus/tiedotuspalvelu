package fi.vm.sade.cloudwatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

@Builder
public record MetricDirective(
    @JsonProperty("Namespace") String namespace,
    @JsonProperty("Dimensions") List<List<String>> dimensions,
    @JsonProperty("Metrics") List<MetricDefinition> metrics) {}

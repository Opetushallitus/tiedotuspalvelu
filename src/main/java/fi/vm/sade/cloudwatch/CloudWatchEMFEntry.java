package fi.vm.sade.cloudwatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;

@Builder
public record CloudWatchEMFEntry(
    @JsonProperty("Timestamp") long timestamp,
    @JsonProperty("CloudWatchMetrics") List<MetricDirective> metricDirectives) {
  public static final String FIELD_AWS_METADATA = "_aws";
}

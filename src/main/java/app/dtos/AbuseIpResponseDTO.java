package app.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbuseIpResponseDTO {

    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String ipAddress;
        private int abuseConfidenceScore;
        private String countryCode;
        private String isp;
        private String domain;
        private int totalReports;

        @JsonProperty("isTor")
        private boolean tor;
    }
}

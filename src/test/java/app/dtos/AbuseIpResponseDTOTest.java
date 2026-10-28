package app.dtos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbuseIpResponseDTOTest {

    @Test
    void shouldReadJsonIntoDto() throws JsonProcessingException {
        String json = """
                {
                  "data": {
                    "ipAddress": "203.0.113.10",
                    "abuseConfidenceScore": 75,
                    "countryCode": "DK",
                    "isp": "Example ISP",
                    "domain": "example.com",
                    "totalReports": 12,
                    "isTor": true
                  }
                }
                """;

        ObjectMapper objectMapper = new ObjectMapper();
        AbuseIpResponseDTO result = objectMapper.readValue(json, AbuseIpResponseDTO.class);

        assertNotNull(result.getData());
        assertEquals("203.0.113.10", result.getData().getIpAddress());
        assertEquals(75, result.getData().getAbuseConfidenceScore());
        assertEquals("DK", result.getData().getCountryCode());
        assertEquals("Example ISP", result.getData().getIsp());
        assertEquals("example.com", result.getData().getDomain());
        assertEquals(12, result.getData().getTotalReports());
        assertTrue(result.getData().isTor());
    }
}

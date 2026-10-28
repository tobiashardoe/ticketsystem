package app.services;

import app.dtos.AbuseIpResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AbuseIpService {

    private String apiKey;

    public AbuseIpService(String apiKey) {
        this.apiKey = apiKey;
    }

    public AbuseIpResponseDTO checkIp(String ipAddress) {
        try {
            String url = "https://api.abuseipdb.com/api/v2/check?ipAddress="
                    + ipAddress + "&maxAgeInDays=90";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Key", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "AbuseIPDB returned HTTP " + response.statusCode());
            }

            ObjectMapper objectMapper = new ObjectMapper();
            AbuseIpResponseDTO result = objectMapper.readValue(
                    response.body(), AbuseIpResponseDTO.class);
            if (result == null || result.getData() == null) {
                throw new IllegalStateException("AbuseIPDB response is missing data");
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("IP lookup was interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Could not read AbuseIPDB response", e);
        }
    }

    public List<AbuseIpResponseDTO> checkMultiple(List<String> ipAddresses) {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<AbuseIpResponseDTO>> futures = new ArrayList<>();
        List<AbuseIpResponseDTO> results = new ArrayList<>();

        for (String ipAddress : ipAddresses) {
            futures.add(executor.submit(() -> checkIp(ipAddress)));
        }

        try {
            for (Future<AbuseIpResponseDTO> future : futures) {
                results.add(future.get());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }

        return results;
    }
}

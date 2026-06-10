package com.sweep.project.ga4;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class Ga4Service {

    private final RestTemplate restTemplate;

    @Value("${ga4.measurement-id}")
    private String measurementId;

    @Value("${ga4.api-secret}")
    private String apiSecret;

    private static final String GA4_ENDPOINT = "https://www.google-analytics.com/mp/collect";

    public void sendDailyActiveUserEvent(Long memberId) {
        CompletableFuture.runAsync(() -> {
            try {
                String url = GA4_ENDPOINT + "?measurement_id=" + measurementId + "&api_secret=" + apiSecret;
                String body = """
                        {
                          "client_id": "%d",
                          "events": [{
                            "name": "daily_active_user",
                            "params": {
                              "member_id": "%d"
                            }
                          }]
                        }
                        """.formatted(memberId, memberId);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
                log.info("GA4 daily_active_user 이벤트 전송 완료: memberId={}", memberId);
            } catch (Exception e) {
                log.warn("GA4 이벤트 전송 실패: memberId={}, error={}", memberId, e.getMessage());
            }
        });
    }
}

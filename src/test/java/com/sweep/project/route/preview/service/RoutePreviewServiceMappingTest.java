package com.sweep.project.route.preview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweep.project.redis.RoutePreviewRedisService;
import com.sweep.project.redis.RouteRedisService;
import com.sweep.project.route.domain.RouteRepository;
import com.sweep.project.route.preview.dto.RoutePreviewDto;
import com.sweep.project.route.preview.util.RouteColorResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoutePreviewServiceMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RoutePreviewService routePreviewService = new RoutePreviewService(
            new RouteColorResolver(),
            mock(RoutePreviewRedisService.class),
            mock(RouteRedisService.class),
            mock(RouteRepository.class),
            mock(RestTemplate.class)
    );

    @Test
    @DisplayName("현재 preview 변환은 ODsay loadLane lane.type으로 지하철 색상을 결정하고 laneName은 빈 문자열로 내려간다")
    void currentPreviewResponseUsesLoadLaneTypeAndLeavesLaneNameBlank() throws Exception {
        String odsayLoadLaneResponse = """
                {
                  "result": {
                    "lane": [
                      {
                        "class": 2,
                        "type": 1,
                        "section": [
                          {
                            "graphPos": "126.978400,37.566000 126.985000,37.572000"
                          }
                        ]
                      },
                      {
                        "class": 2,
                        "type": 2,
                        "section": [
                          {
                            "graphPos": "126.985000,37.572000 126.987000,37.574000"
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        RoutePreviewDto preview = invokeToRoutePreview(odsayLoadLaneResponse);

        printJson("ODsay loadLane response sample", objectMapper.readTree(odsayLoadLaneResponse));
        printJson("Server preview response", preview);

        assertThat(preview.getSegments()).hasSize(2);
        assertThat(preview.getSegments().get(0).getTrafficType()).isEqualTo(1);
        assertThat(preview.getSegments().get(0).getTrafficTypeLabel()).isEqualTo("SUBWAY");
        assertThat(preview.getSegments().get(0).getLaneName()).isEmpty();
        assertThat(preview.getSegments().get(0).getColor()).isEqualTo("#263C96");
        assertThat(preview.getSegments().get(1).getColor()).isEqualTo("#3CB44A");
    }

    @Test
    @DisplayName("ODsay loadLane 지하철 lane.type이 없거나 0이면 현재 코드는 기본 지하철 색상으로 떨어진다")
    void currentPreviewResponseFallsBackWhenLoadLaneTypeIsMissing() throws Exception {
        String odsayLoadLaneResponse = """
                {
                  "result": {
                    "lane": [
                      {
                        "class": 2,
                        "section": [
                          {
                            "graphPos": "126.978400,37.566000 126.985000,37.572000"
                          }
                        ]
                      }
                    ]
                  }
                }
                """;

        RoutePreviewDto preview = invokeToRoutePreview(odsayLoadLaneResponse);

        printJson("ODsay loadLane response sample without lane.type", objectMapper.readTree(odsayLoadLaneResponse));
        printJson("Server preview response", preview);

        assertThat(preview.getSegments()).hasSize(1);
        assertThat(preview.getSegments().get(0).getTrafficType()).isEqualTo(1);
        assertThat(preview.getSegments().get(0).getTrafficTypeLabel()).isEqualTo("SUBWAY");
        assertThat(preview.getSegments().get(0).getLaneName()).isEmpty();
        assertThat(preview.getSegments().get(0).getColor()).isEqualTo("#f97316");
    }

    private RoutePreviewDto invokeToRoutePreview(String odsayLoadLaneResponse) {
        return ReflectionTestUtils.invokeMethod(
                routePreviewService,
                "toRoutePreview",
                odsayLoadLaneResponse,
                "test-route"
        );
    }

    private void printJson(String title, Object value) throws Exception {
        System.out.println();
        System.out.println("==== " + title + " ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}

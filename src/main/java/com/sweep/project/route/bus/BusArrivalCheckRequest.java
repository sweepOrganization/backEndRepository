package com.sweep.project.route.bus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "버스 구간별 제시간 도착 가능 여부 체크 요청")
public class BusArrivalCheckRequest {

    @Schema(description = "목적지 도착 희망 시각", example = "2024-06-01T09:00:00")
    private LocalDateTime desiredArrivalTime;

    @Schema(description = "출발부터 목적지까지 예상 총 소요 시간(초). now + totalSeconds > desiredArrivalTime 이면 즉시 빈 결과 반환", example = "3600")
    private int totalSeconds;

    @Schema(description = """
            버스 구간 맵. key = 탑승 순번 (0: 최초 탑승, 1: 첫 환승 후, ...).
            하나의 구간에 탑승 가능한 버스가 여러 개일 수 있으므로 List로 전달.
            """)
    private Map<Integer, List<BusSegmentQuery>> segments;

    @Schema(description = """
            구간 경계별 도보 시간(초). key = 경계 인덱스.
              0           : 출발지 → 첫 번째 버스 정류장까지 도보
              i (1 이상)  : i-1번 버스 하차 후 i번 버스 정류장까지 환승 도보
              segCount    : 마지막 버스 하차 후 목적지까지 도보
            미입력 키는 0초로 처리하며, 마지막 경계(segCount)가 없으면 totalWalkingMinutes(분)를 초로 환산해 사용한다.
            """,
            example = "{\"0\": 300, \"1\": 180, \"2\": 600}")
    private Map<Integer, Integer> walkSeconds;

    @Data
    @Schema(description = "버스 구간 하나의 탑승 후보")
    public static class BusSegmentQuery {

        @Schema(description = "버스 노선 ID (BIS 기준)", example = "100100118")
        private String busRouteId;

        @Schema(description = "탑승 정류소 ID (BIS 기준)", example = "100000080")
        private String stId;

        @Schema(description = "노선 내 정류소 순번. 0이면 BIS API로 자동 조회", example = "0")
        private int ord;

        @Schema(description = "BIS 제공 기관 코드. 2: 경기도, 4: 서울", example = "4")
        private int providerCode;

        @Schema(description = "이 구간 버스 소요 시간 (분)", example = "15")
        private int sectionTime;
    }
}

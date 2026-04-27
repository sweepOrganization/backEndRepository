package com.sweep.project.route.bus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "버스 탑승 조합별 도착 가능 여부 체크 결과")
public class BusArrivalCheckResult {

    @Schema(description = "가능한 모든 버스 조합의 결과 목록")
    private List<CombinationResult> combinations;

    @Data
    @AllArgsConstructor
    @Schema(description = "버스 탑승 조합 하나의 결과")
    public static class CombinationResult {

        @Schema(description = "이 조합으로 희망 도착 시각(도보 시간 제외)까지 완주 가능한지 여부", example = "true")
        private boolean feasible;

        @Schema(description = "버스 구간만의 예상 완료 시각 (도보 시간 미포함)", example = "08:52:00")
        private LocalTime estimatedBusEndTime;

        @Schema(description = "유효 마감(desiredArrivalTime - walkingMinutes)과의 차이(분). 음수=여유, 양수=지연", example = "-3")
        private long diffMinutes;

        @Schema(description = "구간별 상세 결과. segmentIndex 오름차순")
        private List<SegmentResult> segments;
    }

    @Data
    @AllArgsConstructor
    @Schema(description = "버스 구간 하나의 탑승 결과")
    public static class SegmentResult {

        @Schema(description = "탑승 순번 (0: 최초 탑승, 1: 첫 환승 후, ...)", example = "0")
        private int segmentIndex;

        @Schema(description = "버스 노선 ID", example = "100100118")
        private String busRouteId;

        @Schema(description = "탑승 정류소 ID", example = "100000080")
        private String stId;

        @Schema(description = "BIS 도착 메시지", example = "2분후[1번째 전]")
        private String arrivalMessage;

        @Schema(description = "첫 번째 버스 도착까지 남은 시간 (초)", example = "120")
        private int busArrivalSeconds;

        @Schema(description = "실제 버스 탑승 시각", example = "08:32:00")
        private LocalTime boardingTime;

        @Schema(description = "이 구간 하차 도착 시각", example = "08:47:00")
        private LocalTime segmentEndTime;
    }
}

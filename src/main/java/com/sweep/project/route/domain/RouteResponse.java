package com.sweep.project.route.domain;

import com.sweep.project.route.BoardingInfo;
import com.sweep.project.route.TrafficResponse;
import com.sweep.project.route.bus.BusArrivalCheckResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "경로 탐색 응답. ODsay 경로 목록과 각 경로에 대한 탑승 정보를 포함합니다.")
public class RouteResponse {

    @Schema(description = "ODsay API 기반 교통 경로 목록 (버스/지하철/복합). 경로 없을 경우 null")
    private List<? extends TrafficResponse> trafficResponseList;

    @Schema(description = "각 경로에 대한 탑승 정보 목록 (실시간 도착 정보 및 권장 출발 시각). 경로 없을 경우 null")
    private List<BoardingInfo> boardingInfos;

    @Schema(description = "경로별 최적 버스 조합 탐색 결과 (PATH_TYPE_BUS 일 때만 포함, 최대 3가지 제공)")
    private List<BusArrivalCheckResult> busArrivalResults;
}

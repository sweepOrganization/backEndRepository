package com.sweep.project.route.domain;

import com.sweep.project.route.BoardingInfo;
import com.sweep.project.route.TrafficResponse;
import com.sweep.project.route.bus.BusArrivalCheckResult;
import com.sweep.project.route.bus.BusArrivalInfo;
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

    @Schema(description = "버스에 대한 실시간 도착정보를 포함. 없으면 null")
    private List<BusArrivalInfo> busArrivalInfos;

}

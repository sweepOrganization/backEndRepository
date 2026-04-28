package com.sweep.project.route.controller;

import com.sweep.project.route.bus.BusArrivalCheckRequest;
import com.sweep.project.route.bus.BusArrivalCheckResult;
import com.sweep.project.route.bus.BusArrivalInfo;
import com.sweep.project.route.bus.BusArrivalService;
import com.sweep.project.route.bus.BusRoute;
import com.sweep.project.route.*;
import com.sweep.project.route.domain.PathSearchType;
import com.sweep.project.route.domain.RouteResponse;
import com.sweep.project.route.domain.WalkSegment;
import com.sweep.project.route.dto.RequestBusArrivalInfo;
import com.sweep.project.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/route")
@RequiredArgsConstructor
@Slf4j
public class RouteController {

    private final TrafficRouteStragy trafficRouteStragy;
    private final BusArrivalService busArrivalService;

    @Operation(summary = "Yen's K-Shortest 기반 최적 버스 경로 탐색",
            description = "Yen's K-Shortest Paths 알고리즘으로 희망 도착 시각 내 최적 경로를 비용 오름차순 최대 3개 반환합니다. " +
                    "버스 대기 비용은 '도착시간 - 누적이동시간' 으로 계산되어 환승 순서에 따른 정확한 대기 시간이 반영됩니다.")
    @PostMapping("/bus/best")
    public ApiResponseUtil<BusArrivalCheckResult> findBestBusRoutes(
            @RequestBody BusArrivalCheckRequest request) {
        BusArrivalCheckResult result = busArrivalService.findKBestRoutes(
                request.getDesiredArrivalTime(),
                request.getSegments(), request.getWalkSeconds(),
                request.getTotalSeconds());
        return ApiResponseUtil.SuccessApiResponse("ok", result);
    }
    /**
     * 버스 도착 정보 조회.
     * GET /route/bus/arrival?stId=&busRouteId=&ord=&providerCode=
     *
     * providerCode: 2 = 경기도, 4 = 서울 (기본값)
     * ord가 0이면 BIS API를 통해 자동 조회한다.
     */
    @Operation(summary = "버스의 위치를 조회",description = "특정 노선의 버스가 특정 정류장에 대한 도착정보를 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "생성 성공",
                    headers = {
                            @Header(name = "Authorization", description = "Bearer [Access JWT 토큰]",
                                    schema = @Schema(type = "string")),
                    },
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "권한이 부족합니다.",
                    content = @Content(schema = @Schema(implementation = ApiResponseUtil.class)))
    })
    @GetMapping("/bus/arrival")
    public ApiResponseUtil<List<BusArrivalInfo>> getBusArrival(@RequestBody List<RequestBusArrivalInfo> requestBusArrivalInfos) {
        return ApiResponseUtil.SuccessApiResponse("ok"
                ,busArrivalService.bulkBusArrival(requestBusArrivalInfos));
    }

    /**
     * 탑승 정보 조회.
     * GET /route/boarding?type=PATH_TYPE_SUBWAY&arrivalTime=2024-06-01T09:00:00
     */
    @Operation(summary = "도착지-목적지 루트 조회",description = "도착지 목적지 간의 교통수단에 따른 루트를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "생성 성공",
                    headers = {
                            @Header(name = "Authorization", description = "Bearer [Access JWT 토큰]",
                                    schema = @Schema(type = "string")),
                    },
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "권한이 부족합니다.",
                    content = @Content(schema = @Schema(implementation = ApiResponseUtil.class)))
    })
    @Parameter(name = "Authorization",
            description = "요청시 토큰값을 넣어주셔야됩니다.",
            required = true,
            example = "Bearer [tokenvalue]",
            in = ParameterIn.HEADER)
    @GetMapping("/boarding")
    public ApiResponseUtil<RouteResponse> getBoardingInfo(
            @Parameter(description = "경로 탐색 유형. PATH_TYPE_ANYONE, PATH_TYPE_SUBWAY, PATH_TYPE_BUS", example = "PATH_TYPE_SUBWAY", required = true)
            @RequestParam PathSearchType type,
            @Parameter(description = "출발지 위도", example = "37.5665", required = true)
            @RequestParam double startLat,
            @Parameter(description = "출발지 경도", example = "126.9780", required = true)
            @RequestParam double startLon,
            @Parameter(description = "목적지 위도", example = "37.4979", required = true)
            @RequestParam double endLat,
            @Parameter(description = "목적지 경도", example = "127.0276", required = true)
            @RequestParam double endLon,
            @Parameter(description = "목적지 도착 희망 시각 (ISO 8601 형식)", example = "2024-06-01T09:00:00", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime arrivalTime,
            @Parameter(description = "루트 조회시에 버스에대한 실시간 도착정보가 필요한경우 true값을주사면됩니다", required = true)
            @RequestParam Boolean needRealTimeData) {
        List<? extends TrafficResponse> routes = trafficRouteStragy.getRoutes(type, startLat, startLon, endLat, endLon);
        if (routes.isEmpty()) {
            return ApiResponseUtil.SuccessApiResponse("ok", new RouteResponse(null, null,null));
        }
        List<BoardingInfo> boardingInfos = trafficRouteStragy.getBoardingInfo(type, arrivalTime, routes);


        if(type==PathSearchType.PATH_TYPE_BUS&&needRealTimeData){
            List<RequestBusArrivalInfo> requestBusArrivalInfos=routes.stream().map(x->{
                BusRoute busRoute=(BusRoute) x;

                return busRoute.getSegments().stream().filter(y->{
                    return y.getTrafficType()==TrafficType.TRAFFIC_TYPE_BUS.trafficNumber;
                }).map(y->{
                    return (BusRoute.BusSegment) y;
                }).toList();
            }).flatMap(List::stream).distinct().map(x->{
                return RequestBusArrivalInfo.builder()
                        .busRouteId(x.getLocalBusId())
                        .ord(0)
                        .providerCode(x.getBusProviderCode())
                        .stId(x.getLocalBusStationId())
                        .build();
            }).toList();

            List<BusArrivalInfo> busArrivalInfos=busArrivalService.bulkBusArrival(requestBusArrivalInfos);
            return ApiResponseUtil.SuccessApiResponse("ok", new RouteResponse(routes, boardingInfos,busArrivalInfos));
        }
        return ApiResponseUtil.SuccessApiResponse("ok", new RouteResponse(routes, boardingInfos,null));
    }


}

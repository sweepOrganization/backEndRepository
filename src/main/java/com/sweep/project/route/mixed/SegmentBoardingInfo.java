package com.sweep.project.route.mixed;

import com.sweep.project.route.bus.BusBoardingInfo;
import com.sweep.project.route.subway.SubwayBoardingInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 복합 경로에서 하나의 교통 수단 구간(첫 탑승 또는 환승 지점)에 대한 탑승 정보.
 * <p>
 * trafficType=1(지하철)이면 availableTrains 에, trafficType=2(버스)이면 arrivingBuses 에 값이 채워진다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "복합 경로의 단일 교통 수단 구간 탑승 정보")
public class SegmentBoardingInfo {

    @Schema(description = "교통 수단 유형. 1: 지하철, 2: 버스", example = "1")
    private int trafficType;

    @Schema(description = "탑승 역 또는 정류장 이름", example = "강남역")
    private String stopOrStation;

    @Schema(description = "버스 번호 또는 지하철 노선명", example = "2호선")
    private String transportId;

    @Schema(description = "각 지역 버스노선 ID (BIS 제공 지역 버스 구간에만 존재)", example = "100100118")
    private String localBusId;

    @Schema(description = "버스노선 BIS 제공 기관 코드. 2: 경기도, 4: 서울", example = "4")
    private int busProviderCode;

    @Schema(description = "각 지역 정류장 ID (BIS 제공 지역 버스 구간에만 존재)", example = "100000080")
    private String localStationId;

    @Schema(description = "정류장 BIS 제공 기관 코드. 2: 경기도, 4: 서울", example = "4")
    private int stationProviderCode;

    @Schema(description = "탑승 정류소의 노선 내 순번 (버스 도착 API ord 파라미터)", example = "5")
    private int startStopOrder;

    @Schema(description = "환승 지점 여부. false: 최초 탑승, true: 환승 지점", example = "false")
    private boolean transferPoint;

    @Schema(description = "이 지점에서 늦어도 이 시각에 탑승해야 목적지 도착 시각을 맞출 수 있음. 즉 해당열차가 해당 역에서 이때 출발한다 이소리.", example = "08:45:00")
    private LocalTime latestBoardingTime;

    @Schema(description = "지하철 구간인 경우 이용 가능한 열차 목록 (최대 3편성)-->지금은 1개만 주고있음. 버스 구간이면 null")
    private List<SubwayBoardingInfo.TrainSchedule> availableTrains;

    @Schema(description = "버스 구간인 경우 탑승 정류소에 곧 도착하는 버스 목록 (최대 2대). 지하철 구간이면 null")
    private List<BusBoardingInfo.ArrivingBus> arrivingBuses;

    @Schema(description = "환승 열차가 있는곳까지 사람이 도착하는대 걸리는 시간. 첫 탑승 구간이거나 버스 구간이면 null", example = "08:48:00")
    private LocalTime trainArrivalTime;

    @Schema(description = "환승 도보 이동 소요 시간 (분). 첫 탑승 구간이거나 도보 환승 없으면 0", example = "3")
    private int transferWalkMinutes;
}

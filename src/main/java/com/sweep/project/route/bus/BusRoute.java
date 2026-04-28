package com.sweep.project.route.bus;

import com.sweep.project.route.RouteSegment;
import com.sweep.project.route.TrafficResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Schema(description = "버스 전용 경로 정보 (ODsay pathType=2)")
@Data
@NoArgsConstructor
public class BusRoute implements TrafficResponse {

    @Schema(description = "DB Route ID", example = "10")
    private Long routeId;

    @Schema(description = "총 소요 시간 (분)", example = "38")
    private int totalTime;
    @Schema(description = "요금 (원)", example = "1500")
    private int payment;
    @Schema(description = "환승 횟수", example = "0")
    private int transferCount;
    @Schema(description = "버스 탑승 횟수", example = "1")
    private int busTransitCount;
    @Schema(description = "총 도보 거리 (미터)", example = "250")
    private int totalWalk;
    @Schema(description = "구간 목록. WalkSegment(trafficType=3)·BusSegment(trafficType=2) 순서 유지")
    private List<RouteSegment> segments;
    @Schema(description = "ODsay loadLane API의 mapObject 파라미터 값. 지도 폴리라인 조회 시 사용.만약 값에 0:0@ 이없어도 당황하지 말고 그대로 전달하시면됩니다"
            , example = "0:0@12018:1:5:22")
    private String mapObj;

    public BusRoute(int totalTime, int payment, int transferCount,
                    int busTransitCount, int totalWalk,
                    List<RouteSegment> segments, String mapObj) {
        this.totalTime = totalTime;
        this.payment = payment;
        this.transferCount = transferCount;
        this.busTransitCount = busTransitCount;
        this.totalWalk = totalWalk;
        this.segments = segments;
        this.mapObj = mapObj;
    }

    @Schema(description = "버스 탑승 구간 세부 정보 (trafficType = 2)")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BusSegment implements RouteSegment {

        @Schema(description = "버스 번호", example = "405")
        private String busNo;

        @Schema(description = """
                버스 유형 코드.
                1: 일반버스, 2: 좌석버스, 3: 마을버스, 4: 직행좌석버스,
                5: 공항버스, 6: 간선급행버스, 10: 외곽버스, 11: 간선버스,
                12: 지선버스, 13: 순환버스, 14: 광역버스, 15: 급행버스 16.관광버스 20.농어촌버스 22.경기도 시외형버스 26.급행간선버스
                30.한강버스""",
                example = "11")
        private int busType;

        @Schema(description = "탑승 정류장 이름", example = "서울역")
        private String startStop;

        @Schema(description = "하차 정류장 이름", example = "강남역")
        private String endStop;

        @Schema(description = "탑승~하차 구간 정류장 수", example = "14")
        private int stationCount;

        @Schema(description = "구간 소요 시간 (분)", example = "30")
        private int sectionTime;

        @Schema(description = "구간 거리 (미터)", example = "8400")
        private int distance;

        @Schema(description = "BIS 버스 노선 ID (ODsay lane.busID). /route/bus/arrival의 busRouteId 파라미터로 사용", example = "100100118")
        private int busRouteId;

        @Schema(description = "탑승 정류소 ID (ODsay subPath.startID). /route/bus/arrival의 stId 파라미터로 사용", example = "100000080")
        private int startStopId;

        @Schema(description = "노선 내 탑승 정류소 순번(ord). 아마 값이 0으로 전달될탠대 그냥 가져다가쓰면됩니다. 0이면 /route/bus/arrival 호출 시 서버가 자동 조회", example = "0")
        private int startStopOrder;

        @Schema(description = "지역 BIS 출발 정류장 ID (BIS 제공 지역에만 존재). ODsay subPath.startLocalStationID", example = "124000414")
        private String localBusStationId;

        @Schema(description = "출발 정류장 BIS 제공 기관 코드. 2=경기도, 4=서울", example = "4")
        private int stationProviderCode;

        @Schema(description = "지역 BIS 버스 노선 ID (BIS 제공 지역에만 존재). ODsay lane.busLocalBlID", example = "100100118")
        private String localBusId;

        @Schema(description = "버스 노선 BIS 제공 기관 코드. 2=경기도, 4=서울", example = "4")
        private int busProviderCode;

        @Override
        public int getTrafficType() {
            return 2;
        }
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            BusSegment that = (BusSegment) o;
            return Objects.equals(localBusStationId, that.localBusStationId)
                    && Objects.equals(localBusId, that.localBusId);
        }
        @Override
        public int hashCode() {
            return Objects.hash(localBusStationId, localBusId);
        }
    }
}

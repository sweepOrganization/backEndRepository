package com.sweep.project.route.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;

@NoArgsConstructor
@Getter
public class RequestBusArrivalInfo {

    @NotNull
    @Schema(description = "버스 정류소 ID (BIS 기준)", example = "100000080", required = true)
    public String stId;
    @NotNull
    @Schema(description = "버스 노선 ID (BIS 기준)", example = "100100118", required = true)
    public String busRouteId;
    @NotNull
    @Schema(description = "노선 내 정류소 순번. 0이면 BIS API로 자동 조회", example = "0")
    public int ord;
    @NotNull
    @Schema(description = "버스 정보 제공 기관 코드. 2=경기도, 4=서울(기본값)", example = "4")
    public int providerCode;
}

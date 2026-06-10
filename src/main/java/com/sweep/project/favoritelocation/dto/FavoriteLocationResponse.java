package com.sweep.project.favoritelocation.dto;

import com.sweep.project.favoritelocation.domain.FavoriteLocation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "즐겨찾기 장소 응답")
public class FavoriteLocationResponse {

    @Schema(description = "즐겨찾기 ID", example = "1")
    private final Long id;

    @Schema(description = "즐겨찾기 장소 이름", example = "우리집")
    private final String name;

    @Schema(description = "Kakao map search result place name", example = "신촌역 2호선")
    private final String placeName;

    @Schema(description = "국내 대중교통 경로 탐색에 사용할 도로명 주소", example = "서울특별시 종로구 세종대로 1")
    private final String address;

    @Schema(description = "경도", example = "126.9769")
    private final double x;

    @Schema(description = "위도", example = "37.5796")
    private final double y;

    @Schema(description = "생성 시각", example = "2024-06-01T12:00:00")
    private final LocalDateTime createdAt;

    public FavoriteLocationResponse(FavoriteLocation fl) {
        this.id = fl.getId();
        this.name = fl.getName();
        this.placeName = fl.getPlaceName();
        this.address = fl.getAddress();
        this.x = fl.getX();
        this.y = fl.getY();
        this.createdAt = fl.getCreatedAt();
    }
}

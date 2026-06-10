package com.sweep.project.favoritelocation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FavoriteLocationCreateRequest(

        @Schema(description = "즐겨찾기 장소 이름 (회원당 중복 불가)", example = "우리집", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 50)
        String name,

        @Schema(description = "Kakao map search result place name", example = "신촌역 2호선", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 100)
        String placeName,

        @Schema(description = "국내 대중교통 경로 탐색에 사용할 도로명 주소", example = "서울특별시 종로구 세종대로 1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 255)
        String address,

        @Schema(description = "국내 경도 좌표 (124.0 ~ 132.0)", example = "126.9769", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "124.0", message = "경도는 124.0 이상이어야 합니다.")
        @DecimalMax(value = "132.0", message = "경도는 132.0 이하이어야 합니다.")
        Double x,

        @Schema(description = "국내 위도 좌표 (33.0 ~ 43.0)", example = "37.5796", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "33.0", message = "위도는 33.0 이상이어야 합니다.")
        @DecimalMax(value = "43.0", message = "위도는 43.0 이하이어야 합니다.")
        Double y
) {}

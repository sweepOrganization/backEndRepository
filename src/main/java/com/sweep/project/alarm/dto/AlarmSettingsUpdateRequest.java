package com.sweep.project.alarm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlarmSettingsUpdateRequest(

        // 등록된 알림 수정(준비시간,알림간격,준비물)

        @Schema(description = "준비 시간 (분)", example = "60", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Max(1440)
        Integer prepareTime,

        @Schema(description = "다시알림 간격 (분). 0이면 없음", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Max(240)
        Integer interval,

        @Schema(description = "준비물 (선택)", example = "우산, 지갑")
        @Size(max = 200)
        String checklist

) {}
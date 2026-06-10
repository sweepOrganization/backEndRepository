package com.sweep.project.alarm.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * AlarmZeroOffsetReader 출력 DTO.
 * Alarm + Route 조인 결과를 담는다.
 */
@Getter
@AllArgsConstructor
@Schema(description = "알람 배치 처리 단위 DTO — DB에서 읽어 들인 Alarm + Route 조인 결과")
public class AlarmBatchDto {

    @Schema(description = "알람 ID (zero-offset cursor 기준)", example = "42")
    private Long alarmId;

    @Schema(description = "멤버 ID", example = "7")
    private Long memberId;

    @Schema(description = "사용자 지정 준비 시간(분). null이면 준비 알람을 생성하지 않음", example = "60")
    private Integer prepareTime;

    @Schema(description = "준비 알람 발송 간격(분). null이면 준비 알람을 생성하지 않음", example = "20")
    private Integer interval;

    @Schema(description = "목적지 도착 예정 시각", example = "2024-06-01T09:00:00")
    private LocalDateTime arrivalTime;

    @Schema(description = "Route.totalTime(분). actualTime이 0 또는 null이면 fallback으로 사용", example = "45")
    private Integer totalTime;

    @Schema(description = "실제 소요 시간(분). 0 또는 null이면 Route.totalTime을 사용", example = "40")
    private Integer actualTime;

    @Schema(description = "준비물 입니다", example = "준비물 덩어리")
    private String checkList;
}
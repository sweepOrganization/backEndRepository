package com.sweep.project.alarm.controller;

import com.sweep.project.alarm.dto.*;
import com.sweep.project.alarm.service.AlarmService;
import com.sweep.project.member.service.SecurityMemberReadService;
import com.sweep.project.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "알람", description = "알람 관련 API")
@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;
    private final SecurityMemberReadService securityMemberReadService;

    @Operation(
            summary = "알람 생성",
            description = """
                    새로운 알람을 생성합니다.
                    - isLoop=true 이면 prepareTime, interval, day 는 필수입니다.
                    - startTime 이 오늘 날짜이면 남은 트리거에 한해 즉시 Redis 에 등록됩니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "알람 생성 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (필수 값 누락, 형식 오류 등)",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @PostMapping
    public ApiResponseUtil<AlarmDetailResponse> createAlarm(@Valid @RequestBody AlarmCreateRequest request) {
        AlarmDetailResponse alarm = alarmService.createAlarm(request);
        return ApiResponseUtil.SuccessApiResponse("알람 생성 성공", alarm);
    }

    @Operation(
            summary = "내 알람 목록 조회",
            description = "로그인한 사용자의 알람 목록을 요약 정보(알람 ID, 반복 여부, 반복 요일, 출발 시각, 도착 시각)로 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @GetMapping
    public ApiResponseUtil<AlarmListResponse> getMyAlarms() {
        Long memberId = securityMemberReadService.securityMemberRead().getId();
        return ApiResponseUtil.SuccessApiResponse("내 알람 목록 조회 성공",
                alarmService.getMyAlarms(memberId, LocalDateTime.now()));
    }

    @Operation(
            summary = "알람 상세 조회",
            description = """
                    특정 알람의 상세 정보를 조회합니다.
                    - 알람 기본 정보(반복 여부, 요일, 출발/도착 시각, 준비 시간, 간격, needCheck, 생성 시각)
                    - 연결된 Route 정보(경로 ID, 탐색 유형, 출발·도착 좌표, 총 소요 시간, 경로 원문 JSON)
                    를 함께 반환합니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 알람 ID",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @GetMapping("/{alarmId}")
    public ApiResponseUtil<AlarmDetailResponse> getAlarmDetail(
            @Parameter(description = "조회할 알람 ID", required = true, example = "1")
            @PathVariable Long alarmId
    ) {
        AlarmDetailResponse detail = alarmService.getAlarmDetail(alarmId);
        return ApiResponseUtil.SuccessApiResponse("알람 상세 조회 성공", detail);
    }

    @Operation(
            summary = "알람 수정",
            description = """
                    특정 알람의 정보를 수정합니다.
                    - isLoop=true 이면 prepareTime, day 는 필수입니다.
                    - 수정 즉시 기존 Redis 키가 삭제되고, startTime 이 오늘이면 새 값으로 재등록됩니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "알람 수정 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (필수 값 누락, 형식 오류 등)",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @PutMapping("/{alarmId}")
    public ApiResponseUtil<Void> updateAlarm(
            @Parameter(description = "수정할 알람 ID", required = true, example = "1")
            @PathVariable Long alarmId,
            @Valid @RequestBody AlarmUpdateRequest request
    ) {
        alarmService.updateAlarm(alarmId, request);
        return ApiResponseUtil.SuccessApiResponse("알람 수정 성공", null);
    }

    // 등록된 알림 수정
    @Operation(
            summary = "알람 설정 수정",
            description = "준비시간, 다시알림 간격, 준비물 3가지만 수정합니다. 당일 알람이면 Redis 재등록됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "알람 설정 수정 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패",
                    content = @Content(schema = @Schema())),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @PatchMapping("/{alarmId}/settings")
    public ApiResponseUtil<Void> updateAlarmSettings(
            @Parameter(description = "수정할 알람 ID", required = true, example = "1")
            @PathVariable Long alarmId,
            @Valid @RequestBody AlarmSettingsUpdateRequest request
    ) {
        alarmService.updateAlarmSettings(alarmId, request);
        return ApiResponseUtil.SuccessApiResponse("알람 설정 수정 성공", null);
    }

    @Operation(summary = "알람 삭제", description = "특정 알람을 삭제(soft delete)합니다. 연관된 Redis 키도 함께 제거됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "알람 삭제 성공",
                    useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.",
                    content = @Content(schema = @Schema()))
    })
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @DeleteMapping("/{alarmId}")
    public ApiResponseUtil<Void> deleteAlarm(
            @Parameter(description = "삭제할 알람 ID", required = true, example = "1")
            @PathVariable Long alarmId
    ) {
        alarmService.deleteAlarm(alarmId);
        return ApiResponseUtil.SuccessApiResponse("알람 삭제 성공", null);
    }

    @Operation(summary = "알람 변경사항 체크", description = "특정 알람의 변경사항을 유저가 보았음을 업데이트 합니다. fire and forget 방식으로" +
            "프론트는 해당 api 로 request만 보내고 잊어버리면 됩니다.---->당분간 무시해주세요 고도화 단계입니다.")
    @PostMapping("/needCheck/{alarmId}")
    public void needCheckUpdateWithFireAndForget(
            @Parameter(description = "변경사항 확인한 알람 ID", required = true, example = "1")
            @PathVariable Long alarmId){
        alarmService.fireAndForgetUpdate(alarmId);
    }

    @Operation(summary = "지하철 알람의 경우 실질 시간 업데이트",description = "알람의 경우 actualtime이 초기값이" +
            "0인대 그런 케이스의 경우 처음 조회시에 boarding info를 기반으로 실질시간을 계산하고 나서 서버로 전송해주시면됩니다. " +
            "그러면 알람에 actualtime이 업데이트됩니다")
    @Parameter(name = "Authorization", description = "JWT 액세스 토큰 (Bearer 형식)",
            required = true, example = "Bearer [tokenvalue]", in = ParameterIn.HEADER)
    @PostMapping("/update/actualTime/{alarmId}/{actualTime}")
    public ApiResponseUtil<String> updateActualTime(
            @Parameter(description = "실질 시간 업데이트할 알람 id", required = true, example = "1")
            @PathVariable Long alarmId,
            @Parameter(description = "실질 시간", required = true, example = "1")
            @PathVariable(name = "actualTime") Integer actualTime){
        alarmService.updateAlarmActualTime(alarmId,actualTime);
        return ApiResponseUtil.SuccessApiResponse("실질 시간 업데이트 성공",null);
    }
}
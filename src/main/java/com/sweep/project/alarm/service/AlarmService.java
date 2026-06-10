package com.sweep.project.alarm.service;

import com.sweep.project.alarm.domain.Alarm;
import com.sweep.project.alarm.dto.*;
import com.sweep.project.alarm.repository.AlarmRepository;
import com.sweep.project.alarm.repository.AlarmTicketRepo;
import com.sweep.project.fcm.domain.FcmToken;
import com.sweep.project.fcm.repository.FcmTokenRepository;
import com.sweep.project.member.service.SecurityMemberReadService;
import com.sweep.project.route.domain.Route;
import com.sweep.project.route.domain.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final AlarmRedisService alarmRedisService;
    private final FcmTokenRepository fcmTokenRepository;
    private final RouteRepository routeRepository;
    private final SecurityMemberReadService securityMemberReadService;
    private final AlarmTicketRepo alarmTicketRepo;

    public AlarmDetailResponse createAlarm(AlarmCreateRequest req) {

        Route route = routeRepository.findById(req.routeId())
                .orElseThrow(() -> new RuntimeException("없는 route입니다"));

        LocalDateTime now=LocalDateTime.now();

        if(now.isAfter(req.arrivalTime())||now.isAfter(req.startTime())
                ||req.arrivalTime().isBefore(req.startTime())||req.interval()>req.prepareTime()){
            throw new RuntimeException("등록 할수없는 시간대 혹은 조건입니다");
        }

        Alarm alarm = Alarm.builder()
                .member(securityMemberReadService.securityMemberRead())
                .route(route)
                .title(req.title())
                .checklist(req.checklist())
                .arrivalTime(req.arrivalTime())
                .startTime(req.startTime())
                .prepareTime(req.prepareTime())
                .interval(req.interval())
                .startName(req.startName())
                .endName(req.endName())
                .build();
        alarmRepository.save(alarm);

        Integer totalTime = route.getTotalTime();
        if (totalTime != null) {
            List<String> tokens = fcmTokenRepository.findAllByMemberId(alarm.getMemberId())
                    .stream().map(FcmToken::getToken).collect(Collectors.toList());
            alarmRedisService.registerTodayIfFirable(
                    alarm.getAlarmId(), alarm.getMemberId(), req.startTime(), req.arrivalTime(),
                    alarm.getActualTime(), totalTime, req.prepareTime(), req.interval(), tokens, req.checklist(),now);
        }

        return new AlarmDetailResponse(alarm);
    }

    @Transactional(readOnly = true)
    public AlarmListResponse getMyAlarms(Long memberId, LocalDateTime now) {

        List<Alarm> alarms=alarmTicketRepo.getAlarmList(memberId,now);
        if(alarms.isEmpty()){
            return new AlarmListResponse(null,List.of());
        }
        else{
            AlarmDetailResponse alarmDetailResponse=
                    getAlarmDetail(alarms.getFirst().getAlarmId());

            if(alarms.size()==1){
                return new AlarmListResponse(alarmDetailResponse,List.of());
            }

            List<AlarmSummaryResponse> alarmSummaryResponses=
                    alarms.subList(1,alarms.size()).stream().map(AlarmSummaryResponse::new)
                            .toList();

            return new AlarmListResponse(alarmDetailResponse,alarmSummaryResponses);
        }

    }

    @Transactional(readOnly = true)
    public AlarmDetailResponse getAlarmDetail(Long alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 알람"));
        return new AlarmDetailResponse(alarm);
    }

    public void updateAlarmActualTime(Long alarmId,Integer actualTime){
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 알람"));
        alarm.updateActualTime(actualTime);
    }

    public void updateAlarm(Long alarmId, AlarmUpdateRequest req) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 알람"));
        Route route = routeRepository.findById(req.routeId())
                .orElseThrow(() -> new RuntimeException("없는 route입니다"));

        LocalDateTime now=LocalDateTime.now();
        if(now.isAfter(req.arrivalTime())||now.isAfter(req.startTime())
                ||req.arrivalTime().isBefore(req.startTime())||req.interval()>req.prepareTime()){
            throw new RuntimeException("등록 할수없는 시간대입니다");
        }

        alarmRedisService.deleteAlarmKeys(alarm.getMemberId(), alarm.getAlarmId());
        Integer newInterval = req.interval() != null ? req.interval() : alarm.getInterval();
        alarm.updateAlarm(route, req.arrivalTime(), req.startTime(), req.prepareTime(), newInterval, req.title(), req.checklist());

        Integer totalTime = route.getTotalTime();
        if (totalTime != null) {
            List<String> tokens = fcmTokenRepository.findAllByMemberId(alarm.getMemberId())
                    .stream().map(FcmToken::getToken).collect(Collectors.toList());
            alarmRedisService.registerTodayIfFirable(
                    alarm.getAlarmId(), alarm.getMemberId(), req.startTime(), req.arrivalTime(),
                    alarm.getActualTime(), totalTime, req.prepareTime(), newInterval, tokens, req.checklist(),now);
        }
    }

    // 등록된 알림수정
    public void updateAlarmSettings(Long alarmId, AlarmSettingsUpdateRequest req) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 알람"));

        if (req.interval() > req.prepareTime()) {
            throw new RuntimeException("다시알림 간격은 준비시간보다 클 수 없습니다");
        }

        Integer totalTime = alarm.getRoute().getTotalTime();
        LocalDateTime newStartTime = AlarmTimeCalculator.calculatePrepareStartTime(
                alarm.getArrivalTime(), alarm.getActualTime(), totalTime, req.prepareTime());

        alarmRedisService.deleteAlarmKeys(alarm.getMemberId(), alarm.getAlarmId());
        alarm.updateSettings(newStartTime, req.prepareTime(), req.interval(), req.checklist());

        if (AlarmTimeCalculator.hasTravelTime(alarm.getActualTime(), totalTime)) {
            LocalDateTime now = LocalDateTime.now();
            List<String> tokens = fcmTokenRepository.findAllByMemberId(alarm.getMemberId())
                    .stream().map(FcmToken::getToken).collect(Collectors.toList());
            alarmRedisService.registerTodayIfFirable(
                    alarm.getAlarmId(), alarm.getMemberId(),
                    newStartTime, alarm.getArrivalTime(),
                    alarm.getActualTime(), totalTime, req.prepareTime(), req.interval(),
                    tokens, req.checklist(), now);
        }
    }


    public void fireAndForgetUpdate(Long alarmId){
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 알람"));
        alarm.updateNeedCheck();
    }

    public void deleteAlarm(Long alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 알람"));
        alarm.updateDeleted();
        alarmRedisService.deleteAlarmKeys(alarm.getMemberId(), alarm.getAlarmId());
    }
}
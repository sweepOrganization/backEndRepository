package com.sweep.project.alarm.service;

import java.time.LocalDateTime;

public final class AlarmTimeCalculator {

    /*
    [ 시간 계산 전용 파일입니다 (공통으로 계산하기 위해 모음) ]
    - 알람 수정할때 DB startTime 계산
    - 당일 알람을 Redis에 바로 다시 등록할때
    - 매일 배치가 Redis에 알람 등록할떄

     */


    private AlarmTimeCalculator() {
    }

    // 출발 시간 계산 기본적으로 "departureTime = arrivalTime - actualTime", 단, actualTime이 없다면 totalTime으로 계산됨
    public static LocalDateTime calculateDepartureTime(
            LocalDateTime arrivalTime,
            Integer actualTime,
            Integer totalTime
    ) {
        return arrivalTime.minusMinutes(resolveTravelMinutes(actualTime, totalTime));
    }

    // 첫 준비 알람 시간
    public static LocalDateTime calculatePrepareStartTime(
            LocalDateTime arrivalTime,
            Integer actualTime,
            Integer totalTime,
            Integer prepareTime
    ) {
        LocalDateTime departureTime = calculateDepartureTime(arrivalTime, actualTime, totalTime);
        return departureTime.minusMinutes(prepareTime);
    }

    /* 출발시간을 계산할수있는 이동시간이 있는지 확인
        actualTime 있음 -> 계산 o
        actualTime 없음, totalTime 있음 -> 계산 o
        둘다없다면? -> 계산 x
     */
    public static boolean hasTravelTime(Integer actualTime, Integer totalTime) {
        return (actualTime != null && actualTime > 0) || totalTime != null;
    }

    // 어떤 이동시간을 쓸지 선택 actualTIme or totalTime
    private static int resolveTravelMinutes(Integer actualTime, Integer totalTime) {
        if (actualTime != null && actualTime > 0) {
            return actualTime;
        }
        return totalTime != null ? totalTime : 0;
    }
}
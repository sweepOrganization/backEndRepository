package com.sweep.project.alarm.domain;

import com.sweep.project.member.domain.Member;
import com.sweep.project.route.domain.Route;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alarmId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    private Boolean needCheck = false;
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 50)
    private String title;               // 알림 제목
    @Column(columnDefinition = "text")
    private String checklist;           // 준비물
    private Integer interval;           // 준비 알림 간격 (분)
    private LocalDateTime arrivalTime;  // 도착 시간
    private LocalDateTime startTime;    // 일정 날짜 (최초 알림 시점)
    private Boolean deleted = false;
    private Integer prepareTime;        // 사용자 지정 준비시간 (분)
    private String startName;
    private String endName;
    private Integer actualTime=0;

    @Builder
    public Alarm(Member member, Route route,
                 String title, String checklist,
                 Integer interval,LocalDateTime arrivalTime, LocalDateTime startTime,
                 Integer prepareTime,String startName,String endName,Integer actualTime) {
        this.member = member;
        this.route = route;
        this.title = title;
        this.checklist = checklist;
        this.interval = interval;
        this.arrivalTime = arrivalTime;
        this.startTime = startTime;
        this.prepareTime = prepareTime;
        this.createdAt = LocalDateTime.now();
        this.startName=startName;
        this.endName=endName;
        this.actualTime=actualTime;
    }

    // ── 편의 접근자 ────────────────────────────────────────────────────────────

    public Long getMemberId() { return member.getId(); }
    public Long getRouteId()  { return route.getId(); }

    // ── 변경 메서드 ────────────────────────────────────────────────────────────

    public void updateAlarm(Route route, LocalDateTime arrivalTime, LocalDateTime startTime,
                            Integer prepareTime, Integer interval,
                            String title, String checklist) {
        this.route = route;
        this.arrivalTime = arrivalTime;
        this.startTime = startTime;
        this.prepareTime = prepareTime;
        this.interval = interval;
        this.title = title;
        this.checklist = checklist;
    }

    // 등록된 알림 수정
    public void updateSettings(LocalDateTime startTime, Integer prepareTime, Integer interval, String checklist) {
        this.startTime = startTime;
        this.prepareTime = prepareTime;
        this.interval = interval;
        this.checklist = checklist;
    }

    public void updateActualTime(Integer actualTime){
        this.actualTime=actualTime;
    }
    public void updateDeleted() {
        this.deleted = !this.deleted;
    }

    public void updateNeedCheck() {
        this.needCheck = false;
    }
}
package com.sweep.project.alarm.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sweep.project.alarm.batch.AlarmBatchDto;
import com.sweep.project.alarm.domain.Alarm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static com.sweep.project.alarm.domain.QAlarm.alarm;
import static com.sweep.project.member.domain.QMember.member;
import static com.sweep.project.route.domain.QRoute.route;

/**
 * 알람 배치 전용 QueryDSL 레포지토리.
 *
 * <p>조인 구조
 * <pre>
 *   alarm
 *     └─ join alarm.route  (Route)
 * </pre>
 * 조건: alarm.deleted = false
 */
@Repository
@RequiredArgsConstructor
public class AlarmTicketRepo {

    private final JPAQueryFactory jpaQueryFactory;

    /**
     * 활성 Alarm 의 alarmId min/max 를 반환한다.
     *
     */
    public List<Long> getActiveAlarmMinMaxId(LocalDateTime now,LocalDateTime next) {
        Tuple tuple = jpaQueryFactory
                .select(alarm.alarmId.min(), alarm.alarmId.max())
                .from(alarm)
                .where(alarm.deleted.isFalse()
                        .and(alarm.arrivalTime.between(now,next)))
                .fetchOne();

        if (tuple == null) {
            return Arrays.asList(null, null);
        }
        return Arrays.asList(
                tuple.get(alarm.alarmId.min()),
                tuple.get(alarm.alarmId.max())
        );
    }

    /**
     * zero-offset cursor 기반 페이지 조회.
     *
     * @param minId    파티션 하한 alarmId (포함)
     * @param maxId    파티션 상한 alarmId (포함)
     * @param lastId   직전 페이지 마지막 alarmId (다음 페이지 시작 커서)
     * @param pageSize 한 번에 읽을 행 수
     */
    public List<AlarmBatchDto> fetchActiveAlarmPage(Long minId, Long maxId, Long lastId,
                                                    int pageSize) {
        return jpaQueryFactory
                .select(Projections.constructor(AlarmBatchDto.class,
                        alarm.alarmId,
                        alarm.member.id,
                        alarm.prepareTime,
                        alarm.interval,
                        alarm.arrivalTime,
                        route.totalTime,
                        alarm.checklist))
                .from(alarm)
                .join(alarm.route, route)
                .where(alarm.deleted.isFalse()
                        .and(alarm.alarmId.between(minId, maxId))
                        .and(alarm.alarmId.gt(lastId)))
                .orderBy(alarm.alarmId.asc())
                .limit(pageSize)
                .fetch();
    }

    public List<Alarm> getAlarmList(Long memberId, LocalDateTime currentTime){
        return jpaQueryFactory.select(alarm)
                .from(alarm)
                .join(member)
                .on(alarm.member.eq(member))
                .where(alarm.member.id.eq(memberId).and(alarm.deleted.isFalse())
                        .and(alarm.arrivalTime.after(currentTime)))
                .orderBy(alarm.arrivalTime.asc(),alarm.prepareTime.asc())
                .fetch();
    }
}

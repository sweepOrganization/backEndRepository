package com.sweep.project.alarm.batch;

import com.sweep.project.alarm.service.AlarmTimeCalculator;
import com.sweep.project.fcm.domain.FcmToken;
import com.sweep.project.fcm.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알람 배치 라이터.
 *
 * <h3>처리 흐름</h3>
 * <pre>
 * 1. 청크 내 AlarmBatchDto 를 memberId 기준으로 그룹핑
 * 2. 멤버별 FCM 토큰 조회
 * 3. 각 Alarm 에 대해
 *    a) actualTime(분)-을 우선 사용하고, 없으면 totalTime(분) 사용   // actualTime- '실제' 계산된 이동시간, totalTime- 기본 '예상' 이동 시간
 *    b) departureTime  = arrivalTime - travelTime
 *    c) 출발 알람  1개 : triggerTime = departureTime
 *    d) 준비 알람  N개 : prepareStartTime = departureTime - prepareTime
 *                        N = prepareTime / interval  (정수 나눗셈)
 *                        triggerTime_i = prepareStartTime + i * interval  (i=0..N-1)
 * 4. 각 키에 TTL 설정 후 Redis 에 bulk SET
 *    TTL(ms) = max(0, triggerTime - schedulerRunAt)
 *    → 만료되면 RedisKeyExpirationListener 가 RabbitMQ 로 발행
 * 5. Redis key = "alarm-{memberId}-{alarmId}-{type}-{token}"
 *    PREPARE 시작 알람은 "alarm-{memberId}-{alarmId}-prepare-start-{token}-{i}"
 *    PREPARE 카운트 알람은 "alarm-{memberId}-{alarmId}-prepare-remain-{remainingMinutes}-{token}-{i}"
 *
 *    ex) 준비시간 30분, 간격 10분이라면
 *    1. alarm-1-10-prepare-start-token-0       -> 첫 알림은 prepare-start
 *    2. alarm-1-10-prepare-remain-20-token-1   -> 이후 알림은 prepare-remain-{분}
 *    3. alarm-1-10-prepare-remain-10-token-2
 *
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class AlarmBatchWriter implements ItemWriter<AlarmBatchDto> {

    private static final byte[] EMPTY_VALUE = new byte[0];

    private final StringRedisTemplate redisTemplate;
    private final FcmTokenRepository fcmTokenRepository;

    /** 스케쥴러 실행 시각 — TTL 계산 기준 */
    private final LocalDateTime schedulerRunAt;

    // ── ItemWriter ────────────────────────────────────────────────────────────

    @Override
    public void write(Chunk<? extends AlarmBatchDto> chunk) {
        Map<Long, List<AlarmBatchDto>> grouped = chunk.getItems().stream()
                .collect(Collectors.groupingBy(AlarmBatchDto::getMemberId));

        List<RedisAlarmEntry> entries = new ArrayList<>();
        List<ChecklistEntry> checklistEntries = new ArrayList<>();

        for (Map.Entry<Long, List<AlarmBatchDto>> entry : grouped.entrySet()) {
            Long memberId = entry.getKey();

            List<String> tokens = fcmTokenRepository.findAllByMemberId(memberId)
                    .stream()
                    .map(FcmToken::getToken)
                    .collect(Collectors.toList());

            if (tokens.isEmpty()) {
                log.debug("[AlarmWriter] FCM 토큰 없음 — memberId={} 스킵", memberId);
                continue;
            }

            for (AlarmBatchDto dto : entry.getValue()) {
                collectEntries(dto, memberId, tokens, entries);
                if (AlarmTimeCalculator.hasTravelTime(dto.getActualTime(), dto.getTotalTime())
                        && dto.getArrivalTime() != null
                        && dto.getCheckList() != null && !dto.getCheckList().isBlank()) {
                    LocalDateTime departureTime = AlarmTimeCalculator.calculateDepartureTime(
                            dto.getArrivalTime(), dto.getActualTime(), dto.getTotalTime());
                    long ttl = Math.max(0L, Duration.between(schedulerRunAt, departureTime).toMillis());
                    checklistEntries.add(new ChecklistEntry(
                            "alarm-" + memberId + "-" + dto.getAlarmId() + "-checklist",
                            dto.getCheckList(), ttl));
                }
            }
        }

        if (!entries.isEmpty()) {
            bulkSet(entries);
        }
        if (!checklistEntries.isEmpty()) {
            storeChecklists(checklistEntries);
        }

        log.info("[AlarmWriter] 청크 처리 완료 — 알람 수={} Redis 키 수={}", chunk.size(), entries.size());
    }

    // ── 알람 계산 ─────────────────────────────────────────────────────────────

    private void collectEntries(AlarmBatchDto dto, Long memberId, List<String> tokens,
                                List<RedisAlarmEntry> entries) {
        if (!AlarmTimeCalculator.hasTravelTime(dto.getActualTime(), dto.getTotalTime())) {
            log.warn("[AlarmWriter] travelTime null — alarmId={} 스킵", dto.getAlarmId());
            return;
        }
        /*
         * 이거같은 경우에는 알람 생성시 ARRIVALTIME을 반드시 입력받게 할거라서 고려할 문제는 아닌거같은대 일단은 냅두도록 하겠음.
         * */
        if (dto.getArrivalTime() == null) {
            log.warn("[AlarmWriter] arrivalTime null — alarmId={} 스킵", dto.getAlarmId());
            return;
        }

        LocalDateTime departureTime = AlarmTimeCalculator.calculateDepartureTime(
                dto.getArrivalTime(), dto.getActualTime(), dto.getTotalTime());

        // 출발 알람 1개
        long departureTtl = Math.max(0L, Duration.between(schedulerRunAt, departureTime).toMillis());
        for (String token : tokens) {
            entries.add(new RedisAlarmEntry(
                    buildKey(memberId, dto.getAlarmId(), AlarmType.DEPARTURE, token, null), departureTtl));
        }

        // 준비 알람 N개
        if (dto.getPrepareTime() != null && dto.getInterval() != null && dto.getInterval() > 0) {
            LocalDateTime prepareStart = AlarmTimeCalculator.calculatePrepareStartTime(
                    dto.getArrivalTime(), dto.getActualTime(), dto.getTotalTime(), dto.getPrepareTime());
            int count = dto.getPrepareTime() / dto.getInterval();

            for (int i = 0; i < count; i++) {
                LocalDateTime triggerTime = prepareStart.plusMinutes((long) i * dto.getInterval());
                int remainingMinutes = dto.getPrepareTime() - (i * dto.getInterval());
                long ttlMillis = Math.max(0L, Duration.between(schedulerRunAt, triggerTime).toMillis());
                for (String token : tokens) {
                    entries.add(new RedisAlarmEntry(
                            buildKey(memberId, dto.getAlarmId(), AlarmType.PREPARE, token, i, remainingMinutes), ttlMillis));
                }
            }
        }
    }

    // ── Redis bulk SET ────────────────────────────────────────────────────────

    private void bulkSet(List<RedisAlarmEntry> entries) {
        redisTemplate.executePipelined((org.springframework.data.redis.connection.RedisConnection conn) -> {
            for (RedisAlarmEntry e : entries) {
                byte[] key = e.key().getBytes(StandardCharsets.UTF_8);
                conn.pSetEx(key, e.ttlMillis(), EMPTY_VALUE);
                log.debug("[AlarmWriter] Redis SET — key={} ttl={}ms", e.key(), e.ttlMillis());
            }
            return null;
        });
    }

    private void storeChecklists(List<ChecklistEntry> checklistEntries) {
        redisTemplate.executePipelined((org.springframework.data.redis.connection.RedisConnection conn) -> {
            for (ChecklistEntry e : checklistEntries) {
                conn.pSetEx(e.key().getBytes(StandardCharsets.UTF_8),
                        e.ttlMillis(), e.value().getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
    }

    private String buildKey(Long memberId, Long alarmId, AlarmType type, String token, Integer idx) {
        return buildKey(memberId, alarmId, type, token, idx, null);
    }

    private String buildKey(Long memberId, Long alarmId, AlarmType type, String token, Integer idx,
                            Integer remainingMinutes) {
        String base = "alarm-" + memberId + "-" + alarmId + "-" + type.name().toLowerCase() + "-" + token;
        if (type == AlarmType.PREPARE && remainingMinutes != null) {
            if (idx != null && idx == 0) {
                base = "alarm-" + memberId + "-" + alarmId + "-prepare-start-" + token;
            } else {
                base = "alarm-" + memberId + "-" + alarmId + "-prepare-remain-" + remainingMinutes + "-" + token;
            }
        }
        return idx != null ? base + "-" + idx : base;
    }

    private record RedisAlarmEntry(String key, long ttlMillis) {}
    private record ChecklistEntry(String key, String value, long ttlMillis) {}
}
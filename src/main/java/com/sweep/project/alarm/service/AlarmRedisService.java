package com.sweep.project.alarm.service;

import com.sweep.project.alarm.batch.AlarmType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmRedisService {

    private static final byte[] EMPTY_VALUE = new byte[0];
    private static final long BEFORE_ALARM_MINUTES = 10L;           // 10분 전 알림 기준값
    private static final int BEFORE_ALARM_INDEX_OFFSET = 10000;     // 기본 알림 index(0,1,2...)와 겹치지 않게 크게 띄운 값

    private final StringRedisTemplate redisTemplate;

    // ── 당일 즉시 등록 ──────────────────────────────────────────────────────────

    /**
     * 알람 생성 시 당일 울릴 수 있는 트리거를 즉시 Redis에 등록한다.
     *
     * <ul>
     *   <li>startTime이 오늘 날짜가 아니면 스킵</li>
     *   <li>departure: departureTime > now 인 경우에만 등록</li>
     *   <li>prepare: 각 triggerTime > now 인 항목만 등록</li>
     * </ul>
     */
    public void registerTodayIfFirable(Long alarmId, Long memberId,
                                       LocalDateTime startTime,
                                       LocalDateTime arrivalTime,
                                       int totalTime,
                                       Integer prepareTime, Integer interval,
                                       List<String> tokens) {
        if (tokens.isEmpty()) return;
        if (!startTime.toLocalDate().equals(LocalDate.now())) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = arrivalTime.minusMinutes(totalTime);
        List<RedisAlarmEntry> entries = new ArrayList<>();

        log.info("출발 시간:{}--- 현재시간:{}",departureTime,now);
        log.info("출발 알림이 생성이 가능한가?:{}",departureTime.isAfter(now));

        // 출발 10분 전 알림도 기존 "N분 후에 출발" 문구를 쓰도록 prepare-remain-10 key로 등록한다.
        LocalDateTime departureBeforeTime = departureTime.minusMinutes(BEFORE_ALARM_MINUTES);
        if (departureBeforeTime.isAfter(now)) {
            long ttl = Duration.between(now, departureBeforeTime).toMillis();
            for (String token : tokens) {
                entries.add(new RedisAlarmEntry(
                        buildKey(memberId, alarmId, AlarmType.PREPARE, token,
                                BEFORE_ALARM_INDEX_OFFSET, (int) BEFORE_ALARM_MINUTES), ttl));
            }
        }

        // 출발 알람
        if (departureTime.isAfter(now)) {
            long ttl = Duration.between(now, departureTime).toMillis();
            for (String token : tokens) {
                entries.add(new RedisAlarmEntry(
                        buildKey(memberId, alarmId, AlarmType.DEPARTURE, token, null), ttl));
            }
        }

        // 준비 알림 — interval=0이면 반복 알림은 끄고, 준비 시작/준비 10분 전 알림만 등록한다.
        if (prepareTime != null && interval != null && prepareTime > 0) {
            LocalDateTime prepareStart = departureTime.minusMinutes(prepareTime);

            // 준비 시작 10분 전 알림은 "10분 후에 준비해야 해요" 문구를 위해 prepare-before key로 구분한다.
            LocalDateTime prepareBeforeTime = prepareStart.minusMinutes(BEFORE_ALARM_MINUTES);
            if (prepareBeforeTime.isAfter(now)) {
                long ttl = Duration.between(now, prepareBeforeTime).toMillis();
                for (String token : tokens) {
                    entries.add(new RedisAlarmEntry(
                            buildPrepareBeforeKey(memberId, alarmId, token), ttl));
                }
            }

            if (prepareStart.isAfter(now)) {
                long ttl = Duration.between(now, prepareStart).toMillis();
                for (String token : tokens) {
                    entries.add(new RedisAlarmEntry(
                            buildKey(memberId, alarmId, AlarmType.PREPARE, token, 0, prepareTime), ttl));
                }
            }

            if (interval > 0) {
                int count = prepareTime / interval;
                for (int i = 1; i < count; i++) {
                    LocalDateTime triggerTime = prepareStart.plusMinutes((long) i * interval);
                    int remainingMinutes = prepareTime - (i * interval);
                    if (triggerTime.isAfter(now)) {
                        long ttl = Duration.between(now, triggerTime).toMillis();
                        for (String token : tokens) {
                            entries.add(new RedisAlarmEntry(
                                    buildKey(memberId, alarmId, AlarmType.PREPARE, token, i, remainingMinutes), ttl));
                        }
                    }
                }
            }
        }

        if (!entries.isEmpty()) {
            bulkSet(entries);
            log.info("[AlarmRedisService] 당일 알람 등록 — alarmId={} 키 수={}", alarmId, entries.size());
        }
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────────

    /**
     * 특정 알람에 해당하는 모든 Redis 키를 삭제한다.
     * 패턴: alarm-{memberId}-{alarmId}-*
     */
    public void deleteAlarmKeys(Long memberId, Long alarmId) {
        scanAndDelete("alarm-" + memberId + "-" + alarmId + "-*");
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void bulkSet(List<RedisAlarmEntry> entries) {
        redisTemplate.executePipelined((org.springframework.data.redis.connection.RedisConnection conn) -> {
            for (RedisAlarmEntry e : entries) {
                byte[] key = e.key().getBytes(StandardCharsets.UTF_8);
                conn.pSetEx(key, e.ttlMillis(), EMPTY_VALUE);
                log.debug("[AlarmRedisService] SET — key={} ttl={}ms", e.key(), e.ttlMillis());
            }
            return null;
        });
    }

    private void scanAndDelete(String pattern) {
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(200).build())) {
            List<String> keys = new ArrayList<>();
            cursor.forEachRemaining(keys::add);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[AlarmRedisService] 삭제 완료 — 패턴={} 키 수={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("[AlarmRedisService] Redis 키 삭제 실패 — 패턴={} : {}", pattern, e.getMessage());
        }
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

    private String buildPrepareBeforeKey(Long memberId, Long alarmId, String token) {
        return "alarm-" + memberId + "-" + alarmId + "-prepare-before-" +
                BEFORE_ALARM_MINUTES + "-" + token + "-" + BEFORE_ALARM_INDEX_OFFSET;
    }

    private record RedisAlarmEntry(String key, long ttlMillis) {}
}

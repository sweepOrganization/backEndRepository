package com.sweep.project.fcm.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.sweep.project.alarm.batch.AlarmType;
import com.sweep.project.fcm.domain.FcmSendLog;
import com.sweep.project.redis.RedisMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(FirebaseMessaging.class)
public class FcmSendService {

    // frontend에서 받아온 토큰을 제목과 내용담아서 firebase로 전송하기
    // 여러명한테 동시 알림과 전송 실패처리 아직없음
    private final FirebaseMessaging firebaseMessaging;
    private final FcmSendLogService fcmSendLogService;

    public String sendPush(String token, String title, String body) throws FirebaseMessagingException {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        return firebaseMessaging.send(message);
    }

    public void bulkPush(List<Message> data){
        try {
            BatchResponse response = firebaseMessaging.sendEach(data);
            log.info("FCM bulk send result success={}, failure={}",
                    response.getSuccessCount(),
                    response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException(e);
        }
    }

    // Firebase로 FCM 메시지 일괄 전송, 성공한 메시지만 발송이력 저장
    public void bulkPushWithLog(List<Message> data, List<RedisMessageDto> metadata) {
        if (data.size() != metadata.size()) {
            throw new IllegalArgumentException("FCM 메시지와 로그 메타데이터 수가 일치하지 않습니다.");
        }

        try {
            BatchResponse response = firebaseMessaging.sendEach(data);
            List<SendResponse> sendResponses = response.getResponses();
            List<FcmSendLog> successLogs = IntStream.range(0, sendResponses.size())
                    .mapToObj(i -> createSuccessLog(sendResponses.get(i), metadata.get(i)))
                    .flatMap(Optional::stream)
                    .toList();

            fcmSendLogService.saveSuccessLogs(successLogs);
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<FcmSendLog> createSuccessLog(SendResponse sendResponse, RedisMessageDto logData) {
        // 실패한 전송은 조회 이력에 남기지 않음
        if (!sendResponse.isSuccessful()) {
            log.warn("FCM 전송 실패: {}", sendResponse.getException().getMessage());
            return Optional.empty();
        }

        return Optional.of(FcmSendLog.builder()
                .memberId(Long.valueOf(logData.getMemberId()))
                .alarmId(Long.valueOf(logData.getAlarmId()))
                .alarmType(AlarmType.valueOf(logData.getAlarmType().toUpperCase()))
                .token(logData.getToken())
                .title(buildNotificationTitle())
                .body(buildNotificationBody(logData))
                .firebaseMessageId(sendResponse.getMessageId())
                .sentAt(LocalDateTime.now())
                .build());
    }

    private String buildNotificationTitle() {
        return "호다닥 알림";
    }

    private String buildNotificationBody(RedisMessageDto dto) {
        if ("prepare".equalsIgnoreCase(dto.getAlarmType())) {
            if (Boolean.TRUE.equals(dto.getPrepareStart())) {
                if (dto.getRemainingMinutes() != null) {
                    // 준비 시작 전 사전 알림
                    return dto.getRemainingMinutes() + "분 후에 준비해야 해요";
                }
                return "지금 준비 시작해야 해요";
            }
            if (dto.getRemainingMinutes() != null) {
                return dto.getRemainingMinutes() + "분 후에 출발해야 해요!";
            }
            return "지금 준비 시작해야 해요";
        }
        if ("departure".equalsIgnoreCase(dto.getAlarmType())) {
            return "지금 출발해야 해요!";
        }
        return "알림이 도착했어요";
    }
}

/*
          [로그 저장 값]
 memberId ->            누구에게 보냈는지
 alarmId ->             어떤 알람에서 발생했는지
 alarmType ->           출발 알림/준비 알림 구분
 token ->               발송 대상 기기 토큰
 title/body ->          실제 FCM 메시지 제목/내용
 firebaseMessageId ->   Firebase 성공 응답 메시지 ID
 */

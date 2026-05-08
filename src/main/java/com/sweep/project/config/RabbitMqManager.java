package com.sweep.project.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import com.google.firebase.messaging.Message;
import com.sweep.project.alarm.batch.AlarmMessageDto;
import com.sweep.project.fcm.service.FcmSendService;
import com.sweep.project.fcm.service.FcmTokenService;
import com.sweep.project.redis.RedisMessageDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareBatchMessageListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class RabbitMqManager {
    private final static String dLQName="DLQ";
    private final static String dLXName="DLX";
    private final CachingConnectionFactory connectionFactory;
    private final static String alarmQueueName="alarm";
    private final static String alarmExchangeName="alarmExchange";
    private final RabbitAdmin rabbitAdmin;
    private final ObjectMapper objectMapper;
    private final RetryOperationsInterceptor batchRetryOperationsInterceptor;
    private final FcmSendService fcmSendService;
    private final ConcurrentHashMap<String, SimpleMessageListenerContainer> map
            =new ConcurrentHashMap<>();

    @Value("${fcm.image}")
    private String fcmImage;

    @PostConstruct
    public void setting(){
        //createBasicSetting();
        alarmSetting();
    }
    public void alarmSetting(){
        final String title = buildNotificationTitle();
        Queue actionQueue=createAlramQueue();
        Binding binding= BindingBuilder.bind(actionQueue)
                .to(createAlarmExchange())
                .with(alarmQueueName);
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.declareBinding(binding);
        ChannelAwareBatchMessageListener messageListener= (ChannelAwareBatchMessageListener) (messages, channel)
                -> {
            try {
                List<Message> messages1 = new ArrayList<>();    // messages1 -> Firebase에 실제로 보낼 메시지들
                List<RedisMessageDto> metadata = new ArrayList<>(); // metadata -> log 저장에 필요한 정보들 ex) memberId, token
                messages.stream().forEach(x -> {
                    try {
                        com.sweep.project.redis.RedisMessageDto redisMessageDto= objectMapper.readValue(x.getBody(), RedisMessageDto.class);
                        String body = buildNotificationBody(redisMessageDto);
                        Message message = Message.builder()
                                .setToken(redisMessageDto.getToken())
                                // Android 설정
                                .setAndroidConfig(AndroidConfig.builder()
                                        .setNotification(AndroidNotification.builder()
                                                .setTitle(title)
                                                .setBody(body)
                                                .setIcon(fcmImage)
                                                .setColor("#FF5722")
                                                .setChannelId("default")
                                                .build())
                                        .build())
                                // iOS 설정
                                .setApnsConfig(ApnsConfig.builder()
                                        .setFcmOptions(ApnsFcmOptions.builder()
                                                .setImage(fcmImage)
                                                .build())
                                        .setAps(Aps.builder()
                                                .setAlert(ApsAlert.builder()
                                                        .setTitle(title)
                                                        .setBody(body)
                                                        .build())
                                                .setSound("default")
                                                .build())
                                        .build())
                                // Web 설정
                                .setWebpushConfig(WebpushConfig.builder()
                                        .setNotification(WebpushNotification.builder()
                                                .setTitle(title)
                                                .setBody(body)
                                                .setIcon(fcmImage)
                                                .build())
                                        .build())

                                .build();
                        messages1.add(message);
                        metadata.add(redisMessageDto);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                fcmSendService.bulkPushWithLog(messages1, metadata);
            }
            catch (Exception e){
                throw new RuntimeException(e.getMessage());
            }
        };

        SimpleMessageListenerContainer container=
                new SimpleMessageListenerContainer(connectionFactory);
        container.setMessageListener(messageListener);
        container.addQueueNames(alarmQueueName);
        container.setPrefetchCount(30);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setAdviceChain(batchRetryOperationsInterceptor);
        container.setConcurrentConsumers(3);
        container.setConsumerBatchEnabled(true);
        container.setReceiveTimeout(2000L);
        container.setBatchSize(15);
        container.setRecoveryInterval(30000L);
        container.start();
        map.put(alarmQueueName,container);



    }

    // 사용자에게 뜨는 문구 입니다.
    // *참고* FCM Notification에서 .setTitle(" ")에 들어가면 제목 영역이어서 자동으로 UI에서 굵게 표시된다고합니다.


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

    /*public void createBasicSetting(){
        Queue dlq=createDlq();
        Binding binding=BindingBuilder.bind(dlq)
                .to(createDlx())
                .with(dLQName);
        rabbitAdmin.declareBinding(binding);
        ChannelAwareBatchMessageListener messageListener= (ChannelAwareBatchMessageListener) (messages, channel)
                -> {
            try {
                List<Message> messages1 = new ArrayList<>();
                messages.stream().forEach(x -> {
                    log.info("메시지가 최종적으로 실패:{}",)
                });
                fcmSendService.bulkPush(messages1);
            }
            catch (Exception e){
                throw new RuntimeException(e.getMessage());
            }
        };

        SimpleMessageListenerContainer container=
                new SimpleMessageListenerContainer(connectionFactory);
        container.setMessageListener(messageListener);
        container.addQueueNames(dLQName);
        container.setPrefetchCount(30);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setAdviceChain(batchRetryOperationsInterceptor());
        container.setConcurrentConsumers(1);
        container.setConsumerBatchEnabled(true);
        container.setReceiveTimeout(2000L);
        container.setBatchSize(15);
        container.setRecoveryInterval(30000L);
        container.start();
        map.put(dLQName,container);
    }*/
    private Queue createAlramQueue(){
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dLXName);           // 기본 exchange 사용
        args.put("x-dead-letter-routing-key", dLQName);   // DLQ 이름으로 라우팅
        Queue queue=new Queue(alarmQueueName,true,false,false,args);
        rabbitAdmin.declareQueue(queue);
        return queue;
    }
    private DirectExchange createAlarmExchange(){
        org.springframework.amqp.core.DirectExchange directExchange =
                new DirectExchange(alarmExchangeName,true,false);
        rabbitAdmin.declareExchange(directExchange);
        return directExchange;
    }
    public Queue createDlq(){
        Queue queue=new Queue(dLQName,true,false,false);
        rabbitAdmin.declareQueue(queue);
        return queue;
    }
    public DirectExchange createDlx(){
        DirectExchange directExchange=
                new DirectExchange(dLXName,true,false);
        rabbitAdmin.declareExchange(directExchange);
        return directExchange;
    }
}

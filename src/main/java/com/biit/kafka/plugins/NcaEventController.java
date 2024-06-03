package com.biit.kafka.plugins;

import com.biit.kafka.consumers.EventListener;
import com.biit.kafka.logger.EventsLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;


@Controller
public class NcaEventController {
    private final EventListener eventListener;
    private final NcaEventConsumerListener eventConsumerListener;


    public NcaEventController(@Autowired(required = false) EventListener eventListener,
                              @Autowired(required = false) NcaEventConsumerListener eventConsumerListener) {
        this.eventListener = eventListener;
        this.eventConsumerListener = eventConsumerListener;

        //Listen to topic
        if (eventListener != null) {
            eventListener.addListener((event, offset, groupId, key, partition, topic, timeStamp) -> {
                EventsLogger.debug(this.getClass(), "Received event '{}' on topic '{}', key '{}', partition '{}' at '{}'",
                        event, topic, groupId, key, partition, LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp),
                                TimeZone.getDefault().toZoneId()));
            });
        }
    }
}

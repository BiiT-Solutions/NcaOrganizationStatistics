package com.biit.kafka.plugins;

import com.biit.drools.form.DroolsForm;
import com.biit.kafka.events.KafkaEventTemplate;
import com.biit.kafka.logger.EventsLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NcaEventSender {

    @Value("${spring.kafka.send.topic:}")
    private String sendTopic;

    private final KafkaEventTemplate kafkaTemplate;

    private final EventConverter eventConverter;

    public NcaEventSender(KafkaEventTemplate kafkaTemplate, EventConverter eventConverter) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventConverter = eventConverter;
    }

    public void sendResultEvents(DroolsForm response, String executedBy) {
        EventsLogger.debug(this.getClass().getName(), "Preparing for sending events...");
        if (kafkaTemplate != null && sendTopic != null && !sendTopic.isEmpty()) {
            //Send the complete form as an event.
            kafkaTemplate.send(sendTopic, eventConverter.getEvent(response, executedBy));
            EventsLogger.debug(this.getClass().getName(), "Event with results from '{}' send!", response.getName());
        }
    }
}

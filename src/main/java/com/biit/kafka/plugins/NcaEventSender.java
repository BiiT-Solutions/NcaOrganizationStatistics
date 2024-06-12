package com.biit.kafka.plugins;

import com.biit.drools.form.DroolsForm;
import com.biit.kafka.events.Event;
import com.biit.kafka.events.KafkaEventTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NcaEventSender {

    @Value("${spring.kafka.nca.send.topic:}")
    private String sendTopic;

    private final KafkaEventTemplate kafkaTemplate;

    private final NcaEventConverter ncaEventConverter;

    public NcaEventSender(KafkaEventTemplate kafkaTemplate, NcaEventConverter ncaEventConverter) {
        this.kafkaTemplate = kafkaTemplate;
        this.ncaEventConverter = ncaEventConverter;
    }

    public void sendResultEvents(DroolsForm response, String executedBy, UUID sessionId, String organization) {
        NcaEventsLogger.debug(this.getClass().getName(), "Preparing for sending events...");
        if (kafkaTemplate != null && sendTopic != null && !sendTopic.isEmpty()) {
            //Send the complete form as an event.
            final Event event = ncaEventConverter.getEvent(response, executedBy);
            event.setSessionId(sessionId);
            event.setOrganization(organization);
            kafkaTemplate.send(sendTopic, event);
            NcaEventsLogger.debug(this.getClass().getName(), "Event with results from '{}' send!", response.getName());
        }
    }
}

package com.biit.kafka.plugins;

import com.biit.drools.form.DroolsForm;
import com.biit.kafka.events.Event;
import com.biit.kafka.events.KafkaEventTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnExpression("${spring.kafka.enabled:false}")
public class NcaEventSender {

    @Value("${spring.kafka.nca.send.topic:}")
    private String sendTopic;

    private final KafkaEventTemplate kafkaTemplate;

    private final NcaEventConverter ncaEventConverter;

    private NcaEventSender() {
        this.kafkaTemplate = null;
        this.ncaEventConverter = null;
    }

    @Autowired(required = false)
    public NcaEventSender(KafkaEventTemplate kafkaTemplate, NcaEventConverter ncaEventConverter) {
        this.kafkaTemplate = kafkaTemplate;
        this.ncaEventConverter = ncaEventConverter;
    }

    public void sendResultEvents(DroolsForm response, String executedBy, String organization, UUID sessionId) {
        if (kafkaTemplate != null && sendTopic != null && !sendTopic.isEmpty() && response != null) {
            NcaEventsLogger.debug(this.getClass().getName(), "Preparing for sending events for '{}' ...", response.getName());
            //Send the complete form as an event.
            final Event event = ncaEventConverter.getEvent(response, executedBy);
            event.setSessionId(sessionId);
            event.setOrganization(organization);
            kafkaTemplate.send(sendTopic, event);
            NcaEventsLogger.debug(this.getClass().getName(), "Event with results from '{}' send!", response.getName());
        }
    }
}

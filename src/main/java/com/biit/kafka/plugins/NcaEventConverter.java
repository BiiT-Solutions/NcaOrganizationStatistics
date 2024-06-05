package com.biit.kafka.plugins;

import com.biit.drools.form.DroolsForm;
import com.biit.kafka.events.Event;
import com.biit.kafka.events.EventCustomProperties;
import com.biit.kafka.events.EventSubject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class NcaEventConverter {

    private static final String DROOLS_RESULT_EVENT_TYPE = "DroolsResultForm";

    @Value("${spring.application.name:#{null}}")
    private String applicationName;

    public Event getEvent(DroolsForm droolsForm, String createdBy) {
        final Event event = new Event(droolsForm.getDroolsSubmittedForm());
        event.setCreatedBy(createdBy);
        event.setMessageId(UUID.randomUUID());
        event.setSubject(EventSubject.CREATED.toString());
        event.setContentType(MediaType.APPLICATION_ATOM_XML_VALUE);
        event.setCreatedAt(LocalDateTime.now());
        event.setReplyTo(applicationName);
        if (droolsForm.getLinkedLabel() != null && !droolsForm.getLinkedLabel().isBlank()) {
            event.setTag(droolsForm.getLinkedLabel());
        } else {
            event.setTag(droolsForm.getName());
        }
        event.setCustomProperty(EventCustomProperties.FACT_TYPE, DROOLS_RESULT_EVENT_TYPE);
        event.setCustomProperty(EventCustomProperties.SOURCE_TAG, droolsForm.getName());
        return event;
    }
}

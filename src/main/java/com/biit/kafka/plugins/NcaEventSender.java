package com.biit.kafka.plugins;

/*-
 * #%L
 * NCA Organizatin Statistics Generator
 * %%
 * Copyright (C) 2022 - 2025 BiiT Sourcing Solutions S.L.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

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

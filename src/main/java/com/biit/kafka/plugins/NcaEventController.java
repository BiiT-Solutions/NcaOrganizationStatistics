package com.biit.kafka.plugins;

import com.biit.drools.form.DroolsForm;
import com.biit.drools.form.DroolsSubmittedForm;
import com.biit.drools.form.DroolsSubmittedQuestion;
import com.biit.drools.form.provider.DroolsFormProvider;
import com.biit.factmanager.client.SearchParameters;
import com.biit.factmanager.client.provider.ClientFactProvider;
import com.biit.factmanager.dto.FactDTO;
import com.biit.form.result.FormResult;
import com.biit.kafka.config.ObjectMapperFactory;
import com.biit.kafka.events.Event;
import com.biit.kafka.events.EventCustomProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;


@Controller
public class NcaEventController {
    private static final String NCA_FORM_LABEL = "NCA";
    private static final String NCA_CULTURE_QUESTION_LABEL = "OrgCulture1";
    private static final String NCA_CULTURE_NATURE_LABEL = "OrgNature1";

    private final ClientFactProvider clientFactProvider;
    private final String subscribedTopic;


    public NcaEventController(@Autowired(required = false) NcaEventConsumerListener eventConsumerListener,
                              ClientFactProvider clientFactProvider, NcaEventSender ncaEventSender,
                              @Value("${spring.kafka.nca.topic:}") String subscribedTopic) {
        this.clientFactProvider = clientFactProvider;
        this.subscribedTopic = subscribedTopic;

        //Listen to the topic
        if (eventConsumerListener != null) {
            eventConsumerListener.addListener((event, offset, groupId, key, partition, topic, timeStamp) -> {
                if (Objects.equals(topic, subscribedTopic)) {
                    NcaEventsLogger.debug(this.getClass(), "Received event '{}' on topic '{}', key '{}', partition '{}' at '{}'",
                            event, topic, groupId, key, partition, LocalDateTime.ofInstant(Instant.ofEpochMilli(timeStamp),
                                    TimeZone.getDefault().toZoneId()));
                    final DroolsForm droolsForm = processNca(event);
                    ncaEventSender.sendResultEvents(droolsForm, event.getCreatedBy());
                } else {
                    NcaEventsLogger.debug(this.getClass(), "Ignoring event topic '" + topic + "'.");
                }
            });
        }
    }


    private DroolsForm processNca(Event event) {
        try {
            final FormResult formResult = ObjectMapperFactory.getObjectMapper().readValue(event.getPayload(), FormResult.class);
            //It is a new NCA form??
            if (Objects.equals(formResult.getLabel(), NCA_FORM_LABEL)) {
                final DroolsForm droolsForm = DroolsFormProvider.createStructure(formResult);
                //Gets all forms from the organization.
                final Map<SearchParameters, Object> filter = new HashMap<>();
                filter.putIfAbsent(SearchParameters.APPLICATION, NCA_FORM_LABEL);
                filter.putIfAbsent(SearchParameters.ORGANIZATION, event.getCustomProperty(EventCustomProperties.ORGANIZATION));
                filter.putIfAbsent(SearchParameters.LATEST_BY_USER, "true");
                filter.putIfAbsent(SearchParameters.GROUP, subscribedTopic);
                filter.putIfAbsent(SearchParameters.ELEMENT_NAME, NCA_FORM_LABEL);
                final List<FactDTO> ncaFacts = clientFactProvider.get(filter);

                final Map<String, Integer> answersCount = new HashMap<>();
                for (FactDTO ncaEvent : ncaFacts) {
                    //Read the question values and populate a submittedForm
                    final DroolsSubmittedForm ncaForm = ObjectMapperFactory.getObjectMapper().readValue(ncaEvent.getValue(), DroolsSubmittedForm.class);
                    ncaForm.setSubmittedBy(event.getCreatedBy());
                    ncaForm.getChildren(DroolsSubmittedQuestion.class).forEach(droolsSubmittedQuestion -> {
                        //Main cards question. Stores each value by answer.
                        if (Objects.equals(droolsSubmittedQuestion.getName(), NCA_CULTURE_QUESTION_LABEL)
                                || Objects.equals(droolsSubmittedQuestion.getName(), NCA_CULTURE_NATURE_LABEL)) {
                            if (!droolsSubmittedQuestion.getAnswers().isEmpty()) {
                                final String value = droolsSubmittedQuestion.getAnswers().iterator().next();
                                try {
                                    answersCount.putIfAbsent(droolsSubmittedQuestion.getName() + "_" + value, 0);
                                    answersCount.put(droolsSubmittedQuestion.getName() + "_" + value, answersCount.get(droolsSubmittedQuestion.getName()
                                            + "_" + value)
                                            + 1);
                                } catch (NumberFormatException e) {
                                    NcaEventsLogger.severe(this.getClass(), "Error obtaining the value '{}' from question '{}' at form '{}'.",
                                            value, droolsSubmittedQuestion, ncaForm);
                                }
                            }
                        } else {
                            //Competence cards.
                            if (!droolsSubmittedQuestion.getAnswers().isEmpty()) {
                                final String value = droolsSubmittedQuestion.getAnswers().iterator().next();
                                try {
                                    answersCount.putIfAbsent(droolsSubmittedQuestion.getName(), 0);
                                    answersCount.put(droolsSubmittedQuestion.getName(), answersCount.get(droolsSubmittedQuestion.getName())
                                            + 1);
                                } catch (NumberFormatException e) {
                                    NcaEventsLogger.severe(this.getClass(), "Error obtaining the value '{}' from question '{}' at form '{}'.",
                                            value, droolsSubmittedQuestion, ncaForm);
                                }
                            }
                        }
                    });
                }
                NcaEventsLogger.debug(this.getClass(), "Answers counted '{}'.", answersCount);
                populateVariables(droolsForm, answersCount, ncaFacts.size());
                droolsForm.setTag(NcaEventConverter.FORM_OUTPUT);
                return droolsForm;
            }
        } catch (Exception e) {
            NcaEventsLogger.debug(this.getClass(), "Received event is not a NCA FormResult!");
        }
        return null;
    }

    private void populateVariables(DroolsForm droolsForm, Map<String, Integer> answersCount, double total) {
        answersCount.forEach((key, value) -> {
            final String valueString = System.out.printf("%.2f", value / total).toString();
            NcaEventsLogger.debug(this.getClass(), "Populating variable '{}' with value '{}'.", key, valueString);
            ((DroolsSubmittedForm) droolsForm.getDroolsSubmittedForm())
                    .setVariableValue(droolsForm.getDroolsSubmittedForm(), key, valueString);
        });
    }
}

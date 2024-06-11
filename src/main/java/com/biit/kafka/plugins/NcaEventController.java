package com.biit.kafka.plugins;

import com.biit.drools.form.DroolsForm;
import com.biit.drools.form.DroolsSubmittedForm;
import com.biit.drools.form.provider.DroolsFormProvider;
import com.biit.factmanager.client.SearchParameters;
import com.biit.factmanager.client.provider.ClientFactProvider;
import com.biit.factmanager.dto.FactDTO;
import com.biit.form.result.FormResult;
import com.biit.form.result.QuestionWithValueResult;
import com.biit.form.submitted.ISubmittedObject;
import com.biit.form.submitted.ISubmittedQuestion;
import com.biit.kafka.config.ObjectMapperFactory;
import com.biit.kafka.events.Event;
import com.biit.kafka.events.EventCustomProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;


@Controller
public class NcaEventController {
    private static final String NCA_FORM_LABEL = "NCA";
    private static final String NCA_CULTURE_QUESTION_LABEL = "OrgCulture1";
    private static final String NCA_CULTURE_NATURE_LABEL = "OrgNature1";
    public static final String VARIABLE_NAME = "Percentage";
    private static final String ANSWER_NOT_SELECTED = "0";
    private static final DecimalFormat VALUE_FORMATTER = new DecimalFormat("#0.00", new DecimalFormatSymbols(Locale.ENGLISH));

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
                    if (event != null) {
                        final DroolsForm droolsForm = processNca(event);
                        ncaEventSender.sendResultEvents(droolsForm, event.getCreatedBy(), event.getSessionId());
                    } else {
                        NcaEventsLogger.warning(this.getClass(), "Received null event on topic '" + topic + "'.");
                    }
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
                droolsForm.setSubmittedBy(event.getCreatedBy());
                droolsForm.setTag(NcaEventConverter.FORM_OUTPUT);
                //Gets all forms from the organization.
                final Map<SearchParameters, Object> filter = new HashMap<>();
                filter.putIfAbsent(SearchParameters.APPLICATION, NCA_FORM_LABEL);
                filter.putIfAbsent(SearchParameters.ORGANIZATION, event.getCustomProperty(EventCustomProperties.ORGANIZATION));
                filter.putIfAbsent(SearchParameters.LATEST_BY_USER, "true");
                filter.putIfAbsent(SearchParameters.GROUP, subscribedTopic);
                filter.putIfAbsent(SearchParameters.ELEMENT_NAME, NCA_FORM_LABEL);
                final List<FactDTO> ncaFacts = clientFactProvider.get(filter);

                final Map<ISubmittedObject, Integer> answersCount = new HashMap<>();
                final Map<ISubmittedObject, Map<String, Integer>> archetypesAnswersCount = new HashMap<>();
                for (FactDTO ncaEvent : ncaFacts) {
                    //Read the question values and populate a submittedForm
                    final FormResult ncaForm = ObjectMapperFactory.getObjectMapper().readValue(ncaEvent.getValue(), FormResult.class);
                    ncaForm.getAllChildrenInHierarchy(QuestionWithValueResult.class).forEach(questionWithValueResult -> {
                        //Main cards question. Stores each value by answer.
                        if (Objects.equals(questionWithValueResult.getName(), NCA_CULTURE_QUESTION_LABEL)
                                || Objects.equals(questionWithValueResult.getName(), NCA_CULTURE_NATURE_LABEL)) {
                            if (!questionWithValueResult.getAnswers().isEmpty()) {
                                final String value = questionWithValueResult.getQuestionValues().iterator().next();
                                try {
                                    final ISubmittedQuestion element = (ISubmittedQuestion) droolsForm.getChild(questionWithValueResult.getPathName());
                                    final String answer = element.getAnswers().iterator().next();
                                    archetypesAnswersCount.putIfAbsent(element, new HashMap<>());
                                    archetypesAnswersCount.get(element).putIfAbsent(answer, 0);
                                    archetypesAnswersCount.get(element).put(answer, archetypesAnswersCount.get(element).get(answer) + 1);
                                } catch (NumberFormatException e) {
                                    NcaEventsLogger.severe(this.getClass(), "Error obtaining the value '{}' from question '{}' at form '{}'.",
                                            value, questionWithValueResult, ncaForm);
                                }
                            }
                        } else {
                            //Competence cards.
                            if (!questionWithValueResult.getAnswers().isEmpty()) {
                                final String value = questionWithValueResult.getQuestionValues().iterator().next();
                                if (!Objects.equals(value, ANSWER_NOT_SELECTED)) {
                                    try {
                                        final ISubmittedObject element = droolsForm.getChild(questionWithValueResult.getPathName());
                                        answersCount.putIfAbsent(element, 0);
                                        answersCount.put(element, answersCount.get(element) + 1);
                                    } catch (NumberFormatException e) {
                                        NcaEventsLogger.severe(this.getClass(), "Error obtaining the value '{}' from question '{}' at form '{}'.",
                                                value, questionWithValueResult, ncaForm);
                                    }
                                }
                            }
                        }
                    });
                }
                NcaEventsLogger.debug(this.getClass(), "Answers counted '{}'.", answersCount);
                populateArchetypes(droolsForm, archetypesAnswersCount, ncaFacts.size());
                populateVariables(droolsForm, answersCount, ncaFacts.size());
                droolsForm.setTag(NcaEventConverter.FORM_OUTPUT);
                return droolsForm;
            }
        } catch (JsonProcessingException e) {
            NcaEventsLogger.debug(this.getClass(), "Received event is not a NCA FormResult!");
        } catch (Exception e) {
            NcaEventsLogger.errorMessage(this.getClass(), e);
        }
        return null;
    }

    private void populateVariables(DroolsForm droolsForm, Map<ISubmittedObject, Integer> answersCount, double total) {
        answersCount.forEach((key, value) -> {
            final String valueString = VALUE_FORMATTER.format(value / total);
            NcaEventsLogger.debug(this.getClass(), "Populating variable '{}' with value '{}'.", key, valueString);
            ((DroolsSubmittedForm) droolsForm.getDroolsSubmittedForm())
                    .setVariableValue(key, VARIABLE_NAME, valueString);
        });
    }


    private void populateArchetypes(DroolsForm droolsForm, Map<ISubmittedObject, Map<String, Integer>> archetypesCount, double total) {
        archetypesCount.forEach((key, answers) ->
                answers.forEach((answer, value) -> {
                    final String valueString = VALUE_FORMATTER.format(value / total);
                    NcaEventsLogger.debug(this.getClass(), "Populating variable '{}' with value '{}'.", key, valueString);
                    ((DroolsSubmittedForm) droolsForm.getDroolsSubmittedForm())
                            .setVariableValue(key, VARIABLE_NAME + "_" + answer, valueString);
                }));
    }
}

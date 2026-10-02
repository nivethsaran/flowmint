package com.flowmint.extraction;

import com.flowmint.events.RawEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LangChainLlmClient implements LlmClient {
    private final ExtractionAssistant assistant;
    private final ExtractionFeedbackContext feedback;

    @Autowired
    LangChainLlmClient(ExtractionAssistant assistant, ExtractionFeedbackContext feedback) {
        this.assistant = assistant;
        this.feedback = feedback;
    }

    LangChainLlmClient(ExtractionAssistant assistant) {
        this.assistant = assistant;
        this.feedback = null;
    }

    @Override
    public LlmExtraction extract(RawEvent event) {
        String message = userMessage(event);
        if (feedback != null) {
            String examples = feedback.forEvent(event);
            if (!examples.isBlank()) message += "\n\n" + examples;
        }
        LlmExtraction result = assistant.extract(message);
        if (result == null || result.kind() == null) throw new InvalidExtractionException("empty_response");
        return result;
    }

    static String userMessage(RawEvent event) {
        return """
            Source: %s
            Sender: %s
            App: %s
            Title: %s
            Received at: %s
            Message:
            %s""".formatted(
                event.getSource(), orNone(event.getSender()), orNone(event.getPackageName()), orNone(event.getTitle()),
                event.getEventTimestamp(), event.getBody());
    }

    private static String orNone(String value) { return value == null || value.isBlank() ? "(none)" : value; }
}

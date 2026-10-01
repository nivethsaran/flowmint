package com.flowmint.extraction;

import com.flowmint.events.RawEvent;
import org.springframework.stereotype.Component;

@Component
public class LangChainLlmClient implements LlmClient {
    private final ExtractionAssistant assistant;

    LangChainLlmClient(ExtractionAssistant assistant) { this.assistant = assistant; }

    @Override
    public LlmExtraction extract(RawEvent event) {
        LlmExtraction result = assistant.extract(userMessage(event));
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

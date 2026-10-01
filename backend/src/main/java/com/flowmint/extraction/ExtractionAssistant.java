package com.flowmint.extraction;

import dev.langchain4j.service.SystemMessage;

/** LangChain4j AI service: the return type drives the JSON schema sent as the response format. */
interface ExtractionAssistant {
    @SystemMessage(fromResource = "prompts/extraction-system.txt")
    LlmExtraction extract(String message);
}

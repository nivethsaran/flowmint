package com.flowmint.extraction;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class LlmConfig {
    @Bean
    ChatModel extractionChatModel(LlmProperties properties) {
        return OpenAiChatModel.builder()
            .baseUrl(properties.baseUrl())
            .apiKey(properties.apiKey())
            .modelName(properties.model())
            .temperature(0.0)
            .maxTokens(properties.maxTokens())
            .timeout(properties.timeout())
            .maxRetries(0) // retries are scheduled by the extraction worker with backoff
            .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .logRequests(false) // message bodies must never reach logs
            .logResponses(false)
            .build();
    }

    @Bean
    ExtractionAssistant extractionAssistant(ChatModel extractionChatModel) {
        return AiServices.create(ExtractionAssistant.class, extractionChatModel);
    }

    /** One worker thread: a single llama.cpp slot gains nothing from concurrent requests. */
    @Bean
    ThreadPoolTaskExecutor extractionExecutor(LlmProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("extraction-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}

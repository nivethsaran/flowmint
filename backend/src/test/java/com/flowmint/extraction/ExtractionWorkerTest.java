package com.flowmint.extraction;

import com.flowmint.accounts.AccountType;
import com.flowmint.events.MessageKind;
import com.flowmint.events.ProcessingStatus;
import com.flowmint.events.RawEvent;
import com.flowmint.events.RawEventRepository;
import com.flowmint.transactions.Category;
import com.flowmint.transactions.PaymentChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.ConnectException;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExtractionWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-01T09:05:00Z");
    private static final String SMS = "INR 450.00 debited from A/c XX1234 to VPA swiggy@icici (UPI Ref 4321)";

    private final RawEventRepository rawEvents = mock(RawEventRepository.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final ExtractionResultWriter writer = mock(ExtractionResultWriter.class);
    private final TaskExecutor inline = Runnable::run;
    private RawEvent event;
    private ExtractionWorker worker;

    @BeforeEach
    void setUp() {
        LlmProperties properties = TestData.properties();
        worker = new ExtractionWorker(rawEvents, llm, new ExtractionValidator(properties), new RuleBasedExtractor(), writer, properties, inline, Clock.fixed(NOW, ZoneOffset.UTC));
        event = TestData.event(SMS);
        when(rawEvents.claim(eq(event.getId()), any(), eq(ProcessingStatus.PROCESSING), eq(NOW), eq(NOW.plus(Duration.ofMinutes(5))))).thenReturn(1);
        when(rawEvents.findById(event.getId())).thenReturn(Optional.of(event));
    }

    private void attempts(int count) {
        ReflectionTestUtils.setField(event, "processingAttempts", count);
    }

    @Test
    void savesAValidatedTransaction() {
        attempts(1);
        when(llm.extract(event)).thenReturn(new LlmExtraction(MessageKind.TRANSACTION, "UPI debit", LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 450,
            "INR", "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, PaymentChannel.UPI, "1234", "HDFC Bank", "", "", 0.95));

        worker.process(event.getId());

        ArgumentCaptor<ExtractionResult> result = ArgumentCaptor.forClass(ExtractionResult.class);
        verify(writer).complete(eq(event.getId()), result.capture());
        assertThat(result.getValue().transaction().merchant()).isEqualTo("Swiggy");
    }

    @Test
    void skipsEventsItCannotClaim() {
        when(rawEvents.claim(any(), any(), any(), any(), any())).thenReturn(0);

        worker.process(event.getId());

        verifyNoInteractions(llm, writer);
    }

    @Test
    void schedulesARetryWithBackoffWhenTheLlmFails() {
        attempts(2);
        when(llm.extract(event)).thenThrow(new UncheckedIOException(new ConnectException("refused")));

        worker.process(event.getId());

        verify(writer).retry(event.getId(), "UncheckedIOException", NOW.plus(Duration.ofMinutes(5)));
        verify(writer, never()).complete(any(), any());
    }

    @Test
    void invalidOutputIsRetriedWithItsReasonCode() {
        attempts(1);
        when(llm.extract(event)).thenReturn(new LlmExtraction(MessageKind.TRANSACTION, "", LlmExtraction.Type.EXPENSE, LlmExtraction.Direction.DEBIT, 999,
            "INR", "Swiggy", Category.FOOD_DINING, AccountType.BANK_ACCOUNT, PaymentChannel.UPI, "1234", "", "", "", 0.95));

        worker.process(event.getId());

        verify(writer).retry(event.getId(), "InvalidExtractionException: amount_not_in_message", NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void fallsBackToRulesAfterTheLastAttempt() {
        attempts(5);
        when(llm.extract(event)).thenThrow(new UncheckedIOException(new IOException("down")));

        worker.process(event.getId());

        ArgumentCaptor<ExtractionResult> result = ArgumentCaptor.forClass(ExtractionResult.class);
        verify(writer).complete(eq(event.getId()), result.capture());
        assertThat(result.getValue().transaction().method()).isEqualTo(com.flowmint.transactions.ExtractionMethod.RULES);
        assertThat(result.getValue().transaction().requiresReview()).isTrue();
    }

    @Test
    void failsWhenRulesCannotInterpretTheMessageEither() {
        RawEvent unreadable = TestData.event("Your statement is ready");
        ReflectionTestUtils.setField(unreadable, "processingAttempts", 5);
        when(rawEvents.claim(eq(unreadable.getId()), any(), any(), any(), any())).thenReturn(1);
        when(rawEvents.findById(unreadable.getId())).thenReturn(Optional.of(unreadable));
        when(llm.extract(unreadable)).thenThrow(new IllegalStateException());

        worker.process(unreadable.getId());

        verify(writer).fail(unreadable.getId(), "IllegalStateException");
    }

    @Test
    void doesNotQueueTheSameEventTwice() {
        TaskExecutor deferred = mock(TaskExecutor.class);
        LlmProperties properties = TestData.properties();
        ExtractionWorker queueing = new ExtractionWorker(rawEvents, llm, new ExtractionValidator(properties), new RuleBasedExtractor(), writer, properties, deferred, Clock.fixed(NOW, ZoneOffset.UTC));

        queueing.enqueue(event.getId());
        queueing.enqueue(event.getId());

        verify(deferred, times(1)).execute(any());
    }
}

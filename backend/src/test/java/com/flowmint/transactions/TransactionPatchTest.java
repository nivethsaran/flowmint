package com.flowmint.transactions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmint.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TransactionPatchTest {
    private final ObjectMapper json = new ObjectMapper();

    private TransactionPatch parse(String body) throws Exception {
        return TransactionPatch.parse(json.readTree(body));
    }

    @Test
    void distinguishesAbsentFromNull() throws Exception {
        TransactionPatch absent = parse("{\"merchant\":\"Swiggy\"}");
        assertThat(absent.accountIdPresent()).isFalse();
        assertThat(absent.notesPresent()).isFalse();

        TransactionPatch cleared = parse("{\"accountId\":null,\"notes\":null}");
        assertThat(cleared.accountIdPresent()).isTrue();
        assertThat(cleared.accountId()).isNull();
        assertThat(cleared.notesPresent()).isTrue();
        assertThat(cleared.changesFields()).isTrue();
    }

    @Test
    void reviewedAloneIsNotAnEdit() throws Exception {
        TransactionPatch reviewed = parse("{\"reviewed\":true}");
        assertThat(reviewed.reviewed()).isTrue();
        assertThat(reviewed.changesFields()).isFalse();
    }

    @Test
    void reportsEveryInvalidField() {
        assertThatThrownBy(() -> parse("{\"amount\":-5,\"category\":\"NOPE\",\"date\":\"01/10/2026\",\"bogus\":1,\"applyToMerchant\":true}"))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.status()).isEqualTo(400);
                assertThat(e.fields()).containsOnlyKeys("amount", "category", "date", "bogus", "applyToMerchant");
            });
    }

    @Test
    void rejectsUnknownTypeAndBlankMerchant() {
        assertThatThrownBy(() -> parse("{\"type\":\"UNKNOWN\"}")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse("{\"merchant\":\"   \"}")).isInstanceOf(ApiException.class);
    }
}

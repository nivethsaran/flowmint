package com.flowmint.extraction;

import com.flowmint.events.RawEvent;

/** Seam between the processing pipeline and whichever model performs classification and extraction. */
public interface LlmClient {
    LlmExtraction extract(RawEvent event);
}

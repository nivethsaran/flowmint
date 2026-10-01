package com.flowmint.extraction;

/** The LLM answered, but the answer is not trustworthy. The message is a short code, never message content. */
public class InvalidExtractionException extends RuntimeException {
    public InvalidExtractionException(String code) { super(code); }
}

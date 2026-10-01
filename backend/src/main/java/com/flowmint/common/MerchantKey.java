package com.flowmint.common;

import java.util.Locale;

/** Normalized merchant identity used for grouping, rules, and recurring detection. */
public final class MerchantKey {
    private MerchantKey() {}

    public static String of(String merchant) {
        if (merchant == null) return "";
        return merchant.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

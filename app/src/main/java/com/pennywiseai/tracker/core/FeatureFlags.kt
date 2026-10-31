package com.pennywiseai.tracker.core

object FeatureFlags {
    // Phase 1: Enable strict bank-specific parsers
    const val ENABLE_STRICT_PARSING = false

    // Phase 2: Enable correction logging to DB
    const val ENABLE_CORRECTION_LEARNING = false

    // Phase 3: Suggestion Chips UI
    const val MIN_CORRECTION_COUNT = 2
}

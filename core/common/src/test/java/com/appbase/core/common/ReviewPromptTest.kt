package com.appbase.core.common

import com.appbase.core.common.ReviewPrompt.COOLDOWN_MS
import com.appbase.core.common.ReviewPrompt.MIN_CLEANS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {
    private val now = 1_800_000_000_000L

    @Test
    fun waitsForEnoughCleans() {
        assertFalse(ReviewPrompt.shouldAsk(MIN_CLEANS - 1, 0L, now))
        assertTrue(ReviewPrompt.shouldAsk(MIN_CLEANS, 0L, now))
    }

    @Test
    fun respectsCooldown() {
        assertFalse(ReviewPrompt.shouldAsk(10, now - 1, now))
        assertFalse(ReviewPrompt.shouldAsk(10, now - COOLDOWN_MS + 1, now))
        assertTrue(ReviewPrompt.shouldAsk(10, now - COOLDOWN_MS, now))
    }

    @Test
    fun clockMovedBackDoesNotBlockForever() {
        assertTrue(ReviewPrompt.shouldAsk(10, now + 60_000, now))
    }
}

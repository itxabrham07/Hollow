package com.example.isolation.core.license

import android.os.SystemClock

/**
 * Prevents system clock rollback attacks and offline expiration bypass.
 *
 * Even if the user disconnects from the internet and sets the phone's system date backwards,
 * MonotonicClockGuard compares wall-clock time against:
 * 1. The highest recorded wall-clock timestamp (highWaterWallClockMs).
 * 2. Elapsed monotonic boot uptime (SystemClock.elapsedRealtime()).
 */
object MonotonicClockGuard {

    data class ClockVerificationResult(
        val isValid: Boolean,
        val isClockRolledBack: Boolean,
        val isExpired: Boolean,
        val effectiveCurrentTimeMs: Long,
        val remainingDays: Int,
        val failureReason: String = ""
    )

    fun verifyLicenseWindow(
        issuedServerTimeMs: Long,
        expiresServerTimeMs: Long,
        lastRecordedWallClockMs: Long,
        lastRecordedElapsedRealtimeMs: Long
    ): ClockVerificationResult {
        val nowWallMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()

        // Calculate elapsed time since last check if within the same boot session
        val monotonicDeltaMs = if (nowElapsedMs >= lastRecordedElapsedRealtimeMs && lastRecordedElapsedRealtimeMs > 0L) {
            nowElapsedMs - lastRecordedElapsedRealtimeMs
        } else {
            0L
        }

        val expectedMinimumWallMs = (lastRecordedWallClockMs + monotonicDeltaMs).coerceAtLeast(issuedServerTimeMs)

        // Allow up to 5 minutes of NTP drift; anything larger backwards is a clock rollback tamper
        val driftToleranceMs = 5 * 60 * 1000L
        if (nowWallMs + driftToleranceMs < expectedMinimumWallMs) {
            return ClockVerificationResult(
                isValid = false,
                isClockRolledBack = true,
                isExpired = false,
                effectiveCurrentTimeMs = expectedMinimumWallMs,
                remainingDays = 0,
                failureReason = "System clock rollback detected! Wall clock is behind verified monotonic timestamp."
            )
        }

        val effectiveNow = maxOf(nowWallMs, expectedMinimumWallMs)
        if (expiresServerTimeMs in 1..effectiveNow) {
            return ClockVerificationResult(
                isValid = false,
                isClockRolledBack = false,
                isExpired = true,
                effectiveCurrentTimeMs = effectiveNow,
                remainingDays = 0,
                failureReason = "License token has expired. Connect to server to renew."
            )
        }

        val remainingMs = (expiresServerTimeMs - effectiveNow).coerceAtLeast(0L)
        val remainingDays = (remainingMs / (24L * 3600L * 1000L)).toInt()

        return ClockVerificationResult(
            isValid = true,
            isClockRolledBack = false,
            isExpired = false,
            effectiveCurrentTimeMs = effectiveNow,
            remainingDays = remainingDays
        )
    }
}

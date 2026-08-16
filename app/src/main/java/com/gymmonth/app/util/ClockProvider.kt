package com.gymmonth.app.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable clock for month boundaries and "last synced" timestamps.
 * Tests inject a fixed clock without Android dependencies.
 */
interface ClockProvider {
    fun now(): Instant
    fun zoneId(): ZoneId
    fun clock(): Clock
}

@Singleton
class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun now(): Instant = Instant.now()

    override fun zoneId(): ZoneId = ZoneId.systemDefault()

    override fun clock(): Clock = Clock.systemDefaultZone()
}

/** Fixed clock for unit tests. */
class FixedClockProvider(
    private val instant: Instant,
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : ClockProvider {
    override fun now(): Instant = instant

    override fun zoneId(): ZoneId = zoneId

    override fun clock(): Clock = Clock.fixed(instant, zoneId)
}

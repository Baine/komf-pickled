package snd.komf.ktor

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Simple rate limiter. In burst mode permits are granted up to [eventsPerInterval]
 * in each sliding [interval] window; otherwise permits are spaced evenly across the interval.
 */
class RateLimiter(
    private val eventsPerInterval: Int,
    private val interval: Duration,
    private val allowBurst: Boolean = false,
    warmupPeriod: Duration? = null,
) {
    init {
        require(eventsPerInterval > 0) { "eventsPerInterval must be positive" }
        require(interval.isPositive()) { "interval must be positive" }
    }

    private val mutex = Mutex()
    private val permitSpacing = interval / eventsPerInterval
    private var warmupEnd: TimeSource.Monotonic.ValueTimeMark? =
        warmupPeriod?.let { TimeSource.Monotonic.markNow() + it }
    private var nextAvailable: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
    private val recent: ArrayDeque<TimeSource.Monotonic.ValueTimeMark> = ArrayDeque(eventsPerInterval)

    suspend fun acquire() {
        val now = TimeSource.Monotonic.markNow()
        warmupEnd?.let { if (now < it) return }

        val wakeUp = mutex.withLock {
            if (warmupEnd != null) {
                warmupEnd = null
                nextAvailable = now
                recent.clear()
            }
            if (allowBurst) {
                val cutoff = now - interval
                while (recent.isNotEmpty() && recent.first() <= cutoff) recent.removeFirst()
                if (recent.size < eventsPerInterval) {
                    recent.add(now)
                    return@withLock now
                }
                val wake = recent.first() + interval
                recent.add(wake)
                recent.removeFirst()
                wake
            } else {
                val base = maxOf(nextAvailable, now)
                nextAvailable = base + permitSpacing
                base
            }
        }
        val sleep = (wakeUp - now).inWholeMilliseconds
        if (sleep > 0) delay(sleep)
    }

    suspend fun tryAcquire(timeout: Duration = Duration.ZERO): Boolean {
        val now = TimeSource.Monotonic.markNow()
        warmupEnd?.let { if (now < it) return true }
        val timeoutEnd = now + timeout

        val wakeUp = mutex.withLock {
            if (warmupEnd != null) {
                warmupEnd = null
                nextAvailable = now
                recent.clear()
            }
            if (allowBurst) {
                val cutoff = now - interval
                while (recent.isNotEmpty() && recent.first() <= cutoff) recent.removeFirst()
                if (recent.size < eventsPerInterval) {
                    recent.add(now)
                    return@withLock now
                }
                val wake = recent.first() + interval
                if (wake > timeoutEnd) return@withLock null
                recent.add(wake)
                recent.removeFirst()
                wake
            } else {
                val base = maxOf(nextAvailable, now)
                if (base > timeoutEnd) return@withLock null
                nextAvailable = base + permitSpacing
                base
            }
        } ?: return false

        val sleep = (wakeUp - now).inWholeMilliseconds
        if (sleep > 0) delay(sleep)
        return true
    }
}

fun rateLimiter(eventsPerInterval: Int, interval: Duration, warmupPeriod: Duration? = null): RateLimiter =
    RateLimiter(eventsPerInterval = eventsPerInterval, interval = interval, allowBurst = false, warmupPeriod = warmupPeriod)

fun intervalLimiter(eventsPerInterval: Int, interval: Duration, warmupPeriod: Duration? = null): RateLimiter =
    RateLimiter(eventsPerInterval = eventsPerInterval, interval = interval, allowBurst = true, warmupPeriod = warmupPeriod)

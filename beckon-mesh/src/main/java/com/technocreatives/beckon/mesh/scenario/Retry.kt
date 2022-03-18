package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import arrow.fx.coroutines.Schedule
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

interface Retry {
    suspend operator fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A>
    // retry if it is left and satisfy the predicate
    suspend operator fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A>
}

object NoRetry : Retry {
    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> = f()
    override suspend fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A> = f()
}

@Deprecated("Use InstantRetry")
data class RepeatRetry(val n: Int) : Retry {
    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("Retry 1")
        val res = f()
        if (res.isLeft()) {
            Timber.w("Execute error $res")
            for (i in 2..n) {
                Timber.w("Retry $i")
                val res = f()
                if (i == n)
                    return res
                if (res.isRight())
                    return res
            }
        }
        return res
    }

    override suspend fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A> {
        Timber.w("Retry 1")
        val res = f()
        if (res.isLeft()) {
            Timber.w("Execute error $res")
            for (i in 2..n) {
                Timber.w("Retry $i")
                val res = f()
                if (i == n)
                    return res
                if (res.isRight())
                    return res
            }
        }
        return res
    }
}

data class InstantRetry(val n: Int) : Retry {

    @OptIn(ExperimentalTime::class)
    private fun <A> recur() = Schedule.recurs<A>(n)

    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("InstantRetry")
        val sc = untilRight<E, A>() zipLeft recur()
        return sc.repeat { f() }
    }

    override suspend fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A> {
        Timber.w("InstantRetry")
        val sc = untilRightOr<E, A>(predicate) zipLeft recur()
        return sc.repeat { f() }
    }
}

data class ConstantDelayRetry(val maxRepeat: Int, val interval: Int) : Retry {

    @OptIn(ExperimentalTime::class)
    fun <A> linear() = Schedule.spaced<A>(interval.seconds) zipLeft max(maxRepeat)

    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("ConstantDelayRetry")
        val sc = untilRight<E, A>() zipLeft linear()
        return sc.repeat { f() }
    }

    override suspend fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A> {
        Timber.w("ConstantDelayRetry")
        val sc = untilRightOr<E, A>(predicate) zipLeft linear()
        return sc.repeat { f() }
    }

}

data class LinearRetry(val maxRepeat: Int, val interval: Int) : Retry {

    @OptIn(ExperimentalTime::class)
    fun <A> linear() = Schedule.linear<A>(interval.seconds) zipLeft max(maxRepeat)

    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("LinearRetry")
        val sc = untilRight<E, A>() zipLeft linear()
        return sc.repeat { f() }
    }

    override suspend fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A> {
        Timber.w("LinearRetry")
        val sc = untilRightOr<E, A>(predicate) zipLeft linear()
        return sc.repeat { f() }
    }

}

data class ExponentialBackOffRetry(val maxRepeat: Int, val timeInSeconds: Int) : Retry {
    @OptIn(ExperimentalTime::class)
    fun <A> exp(): Schedule<A, Duration> =
        Schedule.exponential<A>(1.seconds).whileOutput {
            println("Exponential ${it.inWholeSeconds}")
            Timber.w("exp while ${it.inWholeSeconds}")
            it.inWholeSeconds < timeInSeconds.seconds.inWholeSeconds
        } zipLeft max(maxRepeat)

    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("ExponentialBackOffRetry")
        val sc = untilRight<E, A>() zipLeft exp()
        return sc.repeat { f() }
    }

    override suspend fun <E, A> invoke(
        f: suspend () -> Either<E, A>,
        predicate: (E) -> Boolean
    ): Either<E, A> {
        Timber.w("ExponentialBackOffRetry")
        val sc = untilRightOr<E, A>(predicate) zipLeft exp()
        return sc.repeat { f() }
    }
}

private fun <A> max(max: Int) = Schedule.recurs<A>(max)

private fun <A, B> untilRight(): ESchedule<A, B> =
    Schedule.doWhile {
        Timber.w("Execute result: $it")
        it.isLeft()
    }

private fun <A, B> untilRightOr(f: (A) -> Boolean): ESchedule<A, B> =
    Schedule.doWhile {
        Timber.w("Execute result: $it")
        it.fold(f) { true }
    }

private typealias ESchedule<A, B> = Schedule<Either<A, B>, Either<A, B>>
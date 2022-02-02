package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import arrow.fx.coroutines.Schedule
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

interface Retry {
    suspend operator fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A>
}

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
}

data class ExponentialBackOffRetry(val maxRepeat: Int, val totalTime: Int) : Retry {
    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("ExponentialBackOffRetry 1")
        val sc = getSchedule<E, A>(maxRepeat, totalTime)
        return sc.repeat { f() }
    }

}


@OptIn(ExperimentalTime::class)
fun <A> exp(max: Int, totalTime: Int) = Schedule.exponential<A>(1.seconds).whileOutput {
    println("it ${it.inWholeSeconds}")
    Timber.w("exp while ${it.inWholeSeconds}")
    it.inWholeSeconds < totalTime.seconds.inWholeSeconds
} zipLeft max(max)

fun <A> max(max: Int) = Schedule.recurs<A>(max)


@OptIn(ExperimentalTime::class)
fun <A, B> getSchedule(max: Int, totalTime: Int): ESchedule<A, B> =
    Schedule.doWhile<Either<A, B>> {
        Timber.w("Execute error $it")
        it.isLeft()
    } zipLeft exp(max, totalTime)

typealias ESchedule<A, B> = Schedule<Either<A, B>, Either<A, B>>

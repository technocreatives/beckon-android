package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either
import timber.log.Timber

interface Retry {
    suspend operator fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A>
}

data class RepeatRetry(val n: Int) : Retry {
    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        Timber.w("Retry 1")
        val res = f()
        if (res.isLeft()) {
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
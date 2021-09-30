package com.technocreatives.beckon.mesh.scenario

import arrow.core.Either

interface Retry {
    suspend operator fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A>
}

data class RepeatRetry(val n: Int) : Retry {
    override suspend fun <E, A> invoke(f: suspend () -> Either<E, A>): Either<E, A> {
        val res = f()
        for (i in 2..n) {
            val res = f()
            if (i == n)
                return res
            if (res.isRight())
                return res
        }
        return res
    }
}
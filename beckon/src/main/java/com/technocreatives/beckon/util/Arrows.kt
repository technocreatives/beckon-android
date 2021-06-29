package com.technocreatives.beckon.util

import arrow.core.Either
import arrow.core.Nel
import arrow.core.Validated
import arrow.core.nonEmptyListOf
import arrow.core.sequenceValidated
import arrow.typeclasses.Semigroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

fun <E, A> List<Validated<E, A>>.parallelValidate(): Validated<Nel<E>, List<A>> {
    return this.map { it.mapLeft { nonEmptyListOf(it) } }
        .sequenceValidated(Semigroup.nonEmptyList())
}

inline fun <E, T> Flow<Either<E, T>>.filterZ(crossinline f: (T) -> Boolean): Flow<Either<E, T>> {
    return filter { either ->
        either.fold({ true }, { f(it) })
    }
}

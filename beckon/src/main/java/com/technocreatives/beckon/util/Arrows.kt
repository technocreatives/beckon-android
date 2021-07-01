package com.technocreatives.beckon.util

import arrow.core.Either
import arrow.core.Nel
import arrow.core.Validated
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.sequenceValidated
import arrow.typeclasses.Semigroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

fun <E, A> List<Validated<E, A>>.parallelValidate(): Validated<Nel<E>, List<A>> {
    return this.map { it.mapLeft { nonEmptyListOf(it) } }
        .sequenceValidated(Semigroup.nonEmptyList())
}

typealias FlowZ<E, A> = Flow<Either<E, A>>

inline fun <E, T, R> FlowZ<E, T>.mapZ(crossinline mapper: (T) -> R): FlowZ<E, R> {
    return mapEither { mapper(it).right() }
}

inline fun <E1, E2, E, T, R> FlowZ<E1, T>.mapEither(crossinline mapper: (T) -> Either<E2, R>): FlowZ<E, R>
        where E1 : E, E2 : E {
    return map { it.flatMap { t -> mapper(t) } }
}

inline fun <E1, E2, T> FlowZ<E1, T>.mapLeft(crossinline mapper: (E1) -> E2): FlowZ<E2, T> {
    return map { either -> either.mapLeft { mapper(it) } }
}

inline fun <E1, E2, E, T, R> FlowZ<E1, T>.flatMapEither(crossinline mapper: (T) -> FlowZ<E2, R>): FlowZ<E, R>
        where E1 : E, E2 : E {
    return flatMapConcat { it.flatMapFlowEither(mapper) }
}

inline fun <E, T, R> FlowZ<E, T>.flatMapZ(crossinline mapper: (T) -> Flow<R>): FlowZ<E, R> {
    return flatMapConcat { it.flatMapFlow(mapper) }
}

inline fun <E, T> Flow<Either<E, T>>.filterZ(crossinline f: (T) -> Boolean): Flow<Either<E, T>> {
    return filter { either ->
        either.fold({ true }, { f(it) })
    }
}

// @OptIn(ExperimentalTypeInference::class)
// @BuilderInference
fun <E, T, R> Flow<Either<E, T>>.scanZ(
    initialValue: R,
    operation: suspend (accumulator: R, value: T) -> R
): Flow<Either<E, R>> {
    return scan(
        initialValue.right() as Either<E, R>,
        { t1, t2 ->
            t2.map { v2 -> operation(t1.getOrElse { initialValue }, v2) }
        }
    )
}

inline fun <E1, E2, E, T, R> Either<E1, T>.flatMapFlowEither(crossinline mapper: (T) -> FlowZ<E2, R>): FlowZ<E, R>
        where E1 : E, E2 : E {
    return fold({ flowOf(it.left()) }, { mapper(it).map { e -> e } })
}

inline fun <E, T, R> Either<E, T>.flatMapFlow(crossinline mapper: (T) -> Flow<R>): FlowZ<E, R> {
    return flatMapFlowEither { mapper(it).map { r -> r.right() } }
}

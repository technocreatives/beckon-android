package com.technocreatives.beckon.util

import arrow.core.*
import arrow.typeclasses.Semigroup
import kotlinx.coroutines.flow.*

fun <E, A> List<Validated<E, A>>.parallelValidate(): Validated<Nel<E>, List<A>> {
    return this.map { it.mapLeft { nonEmptyListOf(it) } }
        .sequence(Semigroup.nonEmptyList())
}

typealias FlowZ<E, A> = Flow<Either<E, A>>

inline fun <E, T, R> FlowZ<E, T>.mapZ(crossinline mapper: suspend (T) -> R): FlowZ<E, R> {
    return mapEither { mapper(it).right() }
}

inline fun <E1, E2, E, T, R> FlowZ<E1, T>.mapEither(crossinline mapper: suspend (T) -> Either<E2, R>): FlowZ<E, R>
        where E1 : E, E2 : E {
    return map { it.flatMap { t -> mapper(t) } }
}

inline fun <E1, E2, T> FlowZ<E1, T>.mapLeft(crossinline mapper: suspend (E1) -> E2): FlowZ<E2, T> {
    return map { either -> either.mapLeft { mapper(it) } }
}

inline fun <E1, E2, E, T, R> FlowZ<E1, T>.flatMapEither(crossinline mapper: suspend (T) -> FlowZ<E2, R>): FlowZ<E, R>
        where E1 : E, E2 : E {
    return flatMapConcat { it.flatMapFlowEither(mapper) }
}

inline fun <E, T, R> FlowZ<E, T>.flatMapZ(crossinline mapper: suspend (T) -> Flow<R>): FlowZ<E, R> {
    return flatMapConcat { it.flatMapFlow(mapper) }
}

inline fun <E, T> Flow<Either<E, T>>.filterZ(crossinline f: suspend (T) -> Boolean): Flow<Either<E, T>> =
    filter { either ->
        either.fold({ true }, { f(it) })
    }

inline fun <T, R> Flow<T>.filterMap(crossinline f: suspend (T) -> R?): Flow<R> =
    transform { value ->
        val result = f(value)
        if (result != null) return@transform emit(result)
    }


inline fun <E, T, R> Flow<Either<E, T>>.filterMapZ(crossinline f: suspend (T) -> R?): Flow<Either<E, R>> =
    filterMap { either ->
        either.fold({ it.left() }, { f(it)?.let { it.right() } })
    }

// @OptIn(ExperimentalTypeInference::class)
// @BuilderInference
suspend inline fun <E, T, R> Flow<Either<E, T>>.scanZ(
    initialValue: R,
    crossinline operation: suspend (accumulator: R, value: T) -> R
): Flow<Either<E, R>> {
    return scan(
        initialValue.right() as Either<E, R>
    ) { t1, t2 ->
        t2.map { v2 -> operation(t1.getOrElse { initialValue }, v2) }
    }
}

suspend inline fun <E1, E2, E, T, R> Either<E1, T>.flatMapFlowEither(crossinline mapper: suspend (T) -> FlowZ<E2, R>): FlowZ<E, R>
        where E1 : E, E2 : E {
    return fold({ flowOf(it.left()) }, { mapper(it).map { e -> e } })
}

suspend inline fun <E, T, R> Either<E, T>.flatMapFlow(crossinline mapper: suspend (T) -> Flow<R>): FlowZ<E, R> {
    return flatMapFlowEither { mapper(it).map { r -> r.right() } }
}

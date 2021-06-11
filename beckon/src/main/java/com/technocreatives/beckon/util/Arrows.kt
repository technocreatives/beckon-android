package com.technocreatives.beckon.util

import arrow.core.*
import arrow.typeclasses.Semigroup

fun <E, A> List<Validated<E, A>>.parallelValidate(): Validated<List<E>, List<A>> {
    return this.map { it.mapLeft { listOf(it) } }
        .sequenceValidated(Semigroup.list())
}
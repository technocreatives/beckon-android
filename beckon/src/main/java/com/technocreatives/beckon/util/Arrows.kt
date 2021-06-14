package com.technocreatives.beckon.util

import arrow.core.Nel
import arrow.core.Validated
import arrow.core.nonEmptyListOf
import arrow.core.sequenceValidated
import arrow.typeclasses.Semigroup

fun <E, A> List<Validated<E, A>>.parallelValidate(): Validated<Nel<E>, List<A>> {
    return this.map { it.mapLeft { nonEmptyListOf(it) } }
        .sequenceValidated(Semigroup.nonEmptyList())
}

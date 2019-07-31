package com.technocreatives.beckon.util

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.Validated
import arrow.core.ValidatedNel
import arrow.core.extensions.list.traverse.sequence
import arrow.core.extensions.nonemptylist.semigroup.semigroup
import arrow.core.extensions.validated.applicative.applicative
import arrow.core.extensions.validated.functor.map
import arrow.core.fix

fun <E, A> List<Validated<E, A>>.parallelValidate(): ValidatedNel<E, List<A>> {
    return this.map { it.toValidatedNel() }
        .sequence(Validated.applicative(NonEmptyList.semigroup<E>()))
        .map { it.fix() }
}

fun <E, A> Either<E, A>.toValidated() = Validated.fromEither(this)

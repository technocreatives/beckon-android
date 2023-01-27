package com.technocreatives.beckon.mesh.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe


class UnicastAddressTest: StringSpec({
    "Compare unicast address"  {
        val a = UnicastAddress(1)
        val b = UnicastAddress(2)

        b shouldBeGreaterThan a
        a.compareTo(b) shouldBe -1
    }

    "Compare equal unicast address"  {
        val a = UnicastAddress(1)
        val b = UnicastAddress(1)

        b shouldBeEqualComparingTo a
        a.compareTo(b) shouldBe 0
    }
})
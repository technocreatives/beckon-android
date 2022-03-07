package com.technocreatives.beckon.mesh.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe


class PublishableAddressTest: StringSpec({
    "E000"  {
        val ba = byteArrayOf(-32, 0)
        PublishableAddress.from(ba) shouldBe GroupAddress(0xE000)
    }
})
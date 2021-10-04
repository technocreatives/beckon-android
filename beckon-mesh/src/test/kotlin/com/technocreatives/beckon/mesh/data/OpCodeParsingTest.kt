package com.technocreatives.beckon.mesh.data

import com.technocreatives.beckon.extensions.decodeHex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

class OpCodeParsingTest : StringSpec({
    val singleOctetSigMessage = "020608".decodeHex()

    "Single Octet OpCode" {
        val accessPayload = AccessPayload.parse(singleOctetSigMessage)
        accessPayload.opCode should beInstanceOf<OpCode.SingleOctet>()
    }

    val reservedSingleOctetSigMessage = "7F0608".decodeHex()
    "Reserved Single Octet OpCode parsing" {
        val accessPayload = AccessPayload.parse(reservedSingleOctetSigMessage)
        accessPayload.opCode should beInstanceOf<OpCode.ReservedSingleOctet>()
    }

    val doubleOctetOpCodeSigMessage = "820608".decodeHex()
    "Double Octet OpCode parsing" {
        val accessPayload = AccessPayload.parse(doubleOctetOpCodeSigMessage)
        accessPayload.opCode should beInstanceOf<OpCode.DoubleOctet>()
    }

    val tripleOctetOpCodeVendorMessage = "C20608".decodeHex()
    "Triple Octet OpCode parsing" {
        val accessPayload = AccessPayload.parse(tripleOctetOpCodeVendorMessage)
        accessPayload.opCode should beInstanceOf<OpCode.TripleOctet>()
        val tripleOctetOpCode = accessPayload.opCode as OpCode.TripleOctet
        tripleOctetOpCode.companyIdentifier() shouldBeExactly  0x0806
        tripleOctetOpCode.vendorOpCode() shouldBeExactly 0xC2
    }
})
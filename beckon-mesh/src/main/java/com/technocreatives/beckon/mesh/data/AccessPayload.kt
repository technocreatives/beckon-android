package com.technocreatives.beckon.mesh.data

import no.nordicsemi.android.mesh.utils.MeshParserUtils

data class AccessPayload(val opCode: OpCode, val data: ByteArray) {
    companion object {
        fun parse(payload: ByteArray): AccessPayload {
            val opCode = OpCode.parse(payload)
            val data = payload.drop(opCode.octetSize)
            return AccessPayload(opCode, data.toByteArray())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccessPayload

        if (opCode != other.opCode) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = opCode.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

sealed class OpCode {
    abstract val value: Int
    abstract val octetSize: Int

    data class SingleOctet(val byte: Byte) : OpCode() {
        override val octetSize: Int = 1
        override val value: Int = MeshParserUtils.unsignedByteToInt(byte)
    }

    data class ReservedSingleOctet(val byte: Byte) : OpCode() {
        override val octetSize: Int = 1
        override val value: Int = MeshParserUtils.unsignedByteToInt(byte)
    }


    data class DoubleOctet(val byte0: Byte, val byte1: Byte) : OpCode() {
        override val octetSize: Int = 2
        override val value: Int = MeshParserUtils.unsignedBytesToInt(byte1, byte0)
    }

    data class TripleOctet(val byte0: Byte, val byte1: Byte, val byte2: Byte) : OpCode() {
        override val octetSize: Int = 3
        override val value: Int =
            MeshParserUtils.convert24BitsToInt(byteArrayOf(byte0, byte1, byte2))

        fun vendorOpCode() = MeshParserUtils.unsignedByteToInt(byte0)
        fun companyIdentifier(): Int = littleEndianConversion(byteArrayOf(byte1, byte2))
    }

    companion object {
        fun parse(accessPayload: ByteArray): OpCode {
            val firstByte = accessPayload[0]
            return when (MeshParserUtils.getOpCodeLength(MeshParserUtils.unsignedByteToInt(firstByte))) {
                1 -> {
                    if (firstByte == 0x7F.toByte()) {
                        ReservedSingleOctet(accessPayload[0])
                    } else {
                        SingleOctet(accessPayload[0])
                    }
                }
                2 -> DoubleOctet(accessPayload[0], accessPayload[1])
                3 -> TripleOctet(accessPayload[0], accessPayload[1], accessPayload[2])
                else -> throw RuntimeException("Unsupported OpCode Length")
            }
        }
    }
}

fun littleEndianConversion(bytes: ByteArray): Int {
    var result = 0
    for (i in bytes.indices) {
        result = result or (bytes[i].toInt() shl 8 * i)
    }
    return result
}
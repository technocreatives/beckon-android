package com.technocreatives.beckon.mesh.data

data class ProxyFilterMessage(val src: UnicastAddress, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProxyFilterMessage

        if (src != other.src) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = src.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
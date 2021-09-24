package com.technocreatives.beckon.mesh

data class BeckonMeshClientConfig(
    val isDebug: Boolean,
    val ackMessageTimeout: Long,
    val maxProcessingMessages: Int,
)
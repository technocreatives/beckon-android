package com.technocreatives.beckon.mesh.model

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.mesh.data.NodeId

data class BeckonMeshDevice(val device: BeckonDevice, val nodeId: NodeId)

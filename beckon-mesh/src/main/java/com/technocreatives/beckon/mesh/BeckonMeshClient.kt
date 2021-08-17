package com.technocreatives.beckon.mesh

import android.content.Context
import arrow.core.Either
import arrow.core.computations.either
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.mesh.callbacks.AbstractMeshManagerCallbacks
import kotlinx.coroutines.CompletableDeferred
import no.nordicsemi.android.mesh.MeshNetwork
import java.util.*

class BeckonMeshClient(val context: Context, val beckonClient: BeckonClient) {
    private val meshApi = BeckonMeshManagerApi(context)

    suspend fun load(meshUuid: UUID): Either<Any, BeckonMesh> = either {
        // todo check the mesh ID here
        val networkLoadingEmitter =
            CompletableDeferred<Either<MeshLoadFailedError, Unit>>()

        meshApi.setMeshManagerCallbacks(object : AbstractMeshManagerCallbacks() {
            override fun onNetworkLoadFailed(error: String?) {
                networkLoadingEmitter.complete(MeshLoadFailedError("MeshNetwork is empty").left())
            }

            override fun onNetworkLoaded(meshNetwork: MeshNetwork?) {
                networkLoadingEmitter.complete(Unit.right())
            }
        })

        meshApi.loadMeshNetwork()
        networkLoadingEmitter.await().bind()
        meshApi.updateNodes()
        BeckonMesh(context, beckonClient, meshApi)
    }
}
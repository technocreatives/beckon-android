package com.technocreatives.beckon.mesh.state

import arrow.core.*
import arrow.core.computations.either
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.toHex
import com.technocreatives.beckon.mesh.model.AppKey
import com.technocreatives.beckon.mesh.model.NetworkKey
import com.technocreatives.beckon.mesh.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nordicsemi.android.mesh.transport.ConfigAppKeyUpdate
import no.nordicsemi.android.mesh.transport.ConfigKeyRefreshPhaseSet
import no.nordicsemi.android.mesh.transport.ConfigNetKeyUpdate
import no.nordicsemi.android.mesh.utils.MeshParserUtils
import no.nordicsemi.android.mesh.utils.SecureUtils
import timber.log.Timber

suspend fun Connected.distributeNetKey(
    key: NetworkKey,
    newKey: ByteArray
): Either<InvalidNetKey, NetworkKey> =
    withContext(Dispatchers.IO) {
        Either.catch {
            meshApi.meshNetwork().distributeNetKey(key.actualKey, newKey)
                .let { NetworkKey(it) } // todo DistributedFailed
        }.mapLeft {
            Timber.w("========== Invalid Net Key $it")
            InvalidNetKey(newKey)
        }
    }

suspend fun Connected.switchToNewKey(key: NetworkKey): Either<SwitchKeysFailed, no.nordicsemi.android.mesh.NetworkKey> {
    val actualKey = key.actualKey
    return withContext(Dispatchers.IO) {
        Either.catch {
            meshApi.meshNetwork().switchToNewKey(actualKey)
        }.mapLeft { SwitchKeysFailed(key.actualKey) }
            .flatMap {
                if (it) actualKey.right()
                else SwitchKeysFailed(actualKey).left()
            }
    }
}

suspend fun Connected.revokeOldKey(key: no.nordicsemi.android.mesh.NetworkKey): Either<RevokeOldKeyFailed, no.nordicsemi.android.mesh.NetworkKey> =
    withContext(Dispatchers.IO) {
        if (meshApi.meshNetwork().revokeOldKey(key)) {
            key.right()
        } else {
            RevokeOldKeyFailed(key).left()
        }
    }

suspend fun Connected.distributeAppKey(
    key: AppKey,
    newKey: ByteArray
): Either<InvalidAppKey, AppKey> =
    withContext(Dispatchers.IO) {
        Either.catch {
            meshApi.meshNetwork().distributeAppKey(key.applicationKey, newKey)
                .let { AppKey(it) }
        }.mapLeft {
            InvalidAppKey(newKey)
        }
    }


suspend fun Connected.nodeRemoval(address: UnicastAddress): Either<Any, Any> = either {
    bearer.resetConfigNode(address.value).bind()
//    node.netKeys.traverseEither { netKeyRefresh(it) }.bind()
}

suspend fun Connected.netKeyRefresh(netKey: NetworkKey): Either<Any, Any> = either {
    val newKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomNetworkKey())
    Timber.d("======== NewKey: ${newKey.toHex()}")
    Timber.d("======== NetKey: ${netKey.actualKey.info()}")

    val nodes = meshApi.nodes(netKey)
    Timber.d("======== nodes: ${nodes.size}")

    val updatedKey = distributeNetKey(netKey, newKey).bind()
    Timber.d("======== Updated Key: ${updatedKey.actualKey.info()}")

    val updateMessage = ConfigNetKeyUpdate(updatedKey.actualKey)
    val e = nodes.traverseEither { bearer.updateConfigNetKey(it.unicastAddress, updateMessage) }
        .bind()
    Timber.d("======== updateConfigNetKey: ${e.map { it.isSuccessful }}")

    val k1 = switchToNewKey(updatedKey).bind()
    Timber.d("======== switchToNewKey: ${k1.info()}")

    val useNewKeyMessage =
        ConfigKeyRefreshPhaseSet(k1, no.nordicsemi.android.mesh.NetworkKey.USE_NEW_KEYS)
    val e2 =
        nodes.traverseEither {
            bearer.setConfigKeyRefreshPhase(
                it.unicastAddress,
                useNewKeyMessage
            )
        }
            .bind()
    Timber.d("======== useNewKeyMessage: ${e2.map { it.isSuccessful }}")

    val k2 = revokeOldKey(k1).bind()
    Timber.d("======== revokeOldKey: ${k2.info()}")

    val revokeOldKeysMessage =
        ConfigKeyRefreshPhaseSet(k2, no.nordicsemi.android.mesh.NetworkKey.REVOKE_OLD_KEYS)
    val e3 =
        nodes.traverseEither {
            bearer.setConfigKeyRefreshPhase(
                it.unicastAddress,
                revokeOldKeysMessage
            )
        }
            .bind()
    Timber.d("======== revokeOldKeysMessage: ${e3.map { it.isSuccessful }}")

    meshApi.networkKeys().map { Timber.d("New Net Key ${it.key.info()}") }
    e2
}

suspend fun Connected.netKeyRefresh(netKey: NetworkKey, appKey: AppKey): Either<Any, Any> =
    either {
        val newKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomNetworkKey())
        Timber.d("======== NewKey: ${newKey.toHex()}")
        Timber.d("======== NetKey: ${netKey.actualKey.info()}")

        val nodes = meshApi.nodes(netKey)
        Timber.d("======== nodes: ${nodes.size}")

        val updatedKey = distributeNetKey(netKey, newKey).bind()
        Timber.d("======== Updated Key: ${updatedKey.actualKey.info()}")

        val updateMessage = ConfigNetKeyUpdate(updatedKey.actualKey)
        val e =
            nodes.traverseEither { bearer.updateConfigNetKey(it.unicastAddress, updateMessage) }
                .bind()
        Timber.d("======== updateConfigNetKey: ${e.map { it.isSuccessful }}")


        val appKeyRefreshResult = appKeyRefresh(appKey, nodes).bind()
        Timber.d("===== appKeyRefreshResult $appKeyRefreshResult")
        // revert

        val k1 = switchToNewKey(updatedKey).bind()
        Timber.d("======== switchToNewKey: ${k1.info()}")

        val useNewKeyMessage =
            ConfigKeyRefreshPhaseSet(k1, no.nordicsemi.android.mesh.NetworkKey.USE_NEW_KEYS)
        val e2 =
            nodes.traverseEither {
                bearer.setConfigKeyRefreshPhase(
                    it.unicastAddress,
                    useNewKeyMessage
                )
            }
                .bind()
        Timber.d("======== useNewKeyMessage: ${e2.map { it.isSuccessful }}")

        val k2 = revokeOldKey(k1).bind()
        Timber.d("======== revokeOldKey: ${k2.info()}")

        val revokeOldKeysMessage =
            ConfigKeyRefreshPhaseSet(k2, no.nordicsemi.android.mesh.NetworkKey.REVOKE_OLD_KEYS)
        val e3 =
            nodes.traverseEither {
                bearer.setConfigKeyRefreshPhase(
                    it.unicastAddress,
                    revokeOldKeysMessage
                )
            }
                .bind()
        Timber.d("======== revokeOldKeysMessage: ${e3.map { it.isSuccessful }}")

        meshApi.networkKeys().map { Timber.d("New Net Key ${it.key.info()}") }
        e2
    }

suspend fun Connected.appKeyRefresh(appKey: AppKey, nodes: List<Node>): Either<Any, Any> =
    either {

        val newAppKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomApplicationKey())
        Timber.d("======== Current App Key: ${appKey.applicationKey.info()}")
        Timber.d("======== New App Key: ${newAppKey.toHex()}")

        val updatedAppKey = distributeAppKey(appKey, newAppKey).bind()
        Timber.d("======== updatedAppKey: ${updatedAppKey.applicationKey.info()}")

        val updateAppKeyMessage = ConfigAppKeyUpdate(updatedAppKey.applicationKey)
        val e1 =
            nodes.traverseEither {
                bearer.updateConfigAppKey(
                    it.unicastAddress,
                    updateAppKeyMessage
                )
            }.bind()
        meshApi.appKeys().map { Timber.d("New App Key ${it.key.info()}") }
        e1
    }
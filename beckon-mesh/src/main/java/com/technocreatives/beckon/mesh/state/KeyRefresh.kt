package com.technocreatives.beckon.mesh.state

import arrow.core.*
import arrow.core.continuations.either
import com.technocreatives.beckon.mesh.*
import com.technocreatives.beckon.mesh.data.AppKey
import com.technocreatives.beckon.mesh.data.NetKey
import com.technocreatives.beckon.mesh.data.Node
import com.technocreatives.beckon.mesh.data.UnicastAddress
import com.technocreatives.beckon.mesh.extensions.info
import com.technocreatives.beckon.mesh.extensions.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nordicsemi.android.mesh.ApplicationKey
import no.nordicsemi.android.mesh.NetworkKey
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
            meshApi.meshNetwork().distributeNetKey(key, newKey)
        }.mapLeft {
            Timber.w("========== Invalid Net Key $it")
            InvalidNetKey(newKey)
        }
    }

suspend fun Connected.switchToNewKey(key: NetworkKey): Either<SwitchKeysFailed, NetworkKey> {
    return withContext(Dispatchers.IO) {
        Either.catch {
            meshApi.meshNetwork().switchToNewKey(key)
        }.mapLeft { SwitchKeysFailed(key) }
            .flatMap {
                if (it) key.right()
                else SwitchKeysFailed(key).left()
            }
    }
}

suspend fun Connected.revokeOldKey(key: NetworkKey): Either<RevokeOldKeyFailed, NetworkKey> =
    withContext(Dispatchers.IO) {
        if (meshApi.meshNetwork().revokeOldKey(key)) {
            key.right()
        } else {
            RevokeOldKeyFailed(key).left()
        }
    }

suspend fun Connected.distributeAppKey(
    key: ApplicationKey,
    newKey: ByteArray
): Either<InvalidAppKey, ApplicationKey> =
    withContext(Dispatchers.IO) {
        Either.catch {
            meshApi.meshNetwork().distributeAppKey(key, newKey)
        }.mapLeft {
            InvalidAppKey(newKey)
        }
    }


suspend fun Connected.nodeRemoval(address: UnicastAddress): Either<Any, Any> = either {
    bearer.resetConfigNode(address.value).bind()
//    node.netKeys.traverseEither { netKeyRefresh(it) }.bind()
}

suspend fun Connected.netKeyRefresh(netKey: NetKey): Either<Any, Any> = either {
    val newKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomNetworkKey())
    val networkKey = beckonMesh.netKey(netKey.index)!!
    Timber.d("======== NewKey: ${newKey.toHex()}")
    Timber.d("======== NetKey: $netKey")

    val nodes = meshApi.nodes(netKey)
    Timber.d("======== nodes: ${nodes.size}")

    val updatedKey = distributeNetKey(networkKey, newKey).bind()
    Timber.d("======== Updated Key: ${updatedKey.info()}")

    val updateMessage = ConfigNetKeyUpdate(updatedKey)
    val e =
        nodes.traverseEither { bearer.updateConfigNetKey(it.unicastAddress.value, updateMessage) }
            .bind()
    Timber.d("======== updateConfigNetKey: ${e.map { it.isSuccessful }}")

    val k1 = switchToNewKey(updatedKey).bind()
    Timber.d("======== switchToNewKey: ${k1.info()}")

    val useNewKeyMessage =
        ConfigKeyRefreshPhaseSet(k1, NetworkKey.USE_NEW_KEYS)
    val e2 =
        nodes.traverseEither {
            bearer.setConfigKeyRefreshPhase(
                it.unicastAddress.value,
                useNewKeyMessage
            )
        }
            .bind()
    Timber.d("======== useNewKeyMessage: ${e2.map { it.isSuccessful }}")

    val k2 = revokeOldKey(k1).bind()
    Timber.d("======== revokeOldKey: ${k2.info()}")

    val revokeOldKeysMessage =
        ConfigKeyRefreshPhaseSet(k2, NetworkKey.REVOKE_OLD_KEYS)
    val e3 =
        nodes.traverseEither {
            bearer.setConfigKeyRefreshPhase(
                it.unicastAddress.value,
                revokeOldKeysMessage
            )
        }
            .bind()
    Timber.d("======== revokeOldKeysMessage: ${e3.map { it.isSuccessful }}")

    beckonMesh.networkKeys().map { Timber.d("New Net Key $it") }
    e2
}

suspend fun Connected.netKeyRefresh(netKey: NetKey, appKey: AppKey): Either<Any, Any> =
    either {
        val networkKey = beckonMesh.netKey(netKey.index)!!
        val newKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomNetworkKey())
        Timber.d("======== NewKey: ${newKey.toHex()}")
        Timber.d("======== NetKey: ${networkKey.info()}")

        val nodes = meshApi.nodes(netKey)
        Timber.d("======== nodes: ${nodes.size}")

        val updatedKey = distributeNetKey(networkKey, newKey).bind()
        Timber.d("======== Updated Key: ${updatedKey.info()}")

        val updateMessage = ConfigNetKeyUpdate(updatedKey)
        val e =
            nodes.traverseEither {
                bearer.updateConfigNetKey(
                    it.unicastAddress.value,
                    updateMessage
                )
            }
                .bind()
        Timber.d("======== updateConfigNetKey: ${e.map { it.isSuccessful }}")


        val appKeyRefreshResult = appKeyRefresh(appKey, nodes).bind()
        Timber.d("===== appKeyRefreshResult $appKeyRefreshResult")
        // revert

        val k1 = switchToNewKey(updatedKey).bind()
        Timber.d("======== switchToNewKey: ${k1.info()}")

        val useNewKeyMessage =
            ConfigKeyRefreshPhaseSet(k1, NetworkKey.USE_NEW_KEYS)
        val e2 =
            nodes.traverseEither {
                bearer.setConfigKeyRefreshPhase(
                    it.unicastAddress.value,
                    useNewKeyMessage
                )
            }
                .bind()
        Timber.d("======== useNewKeyMessage: ${e2.map { it.isSuccessful }}")

        val k2 = revokeOldKey(k1).bind()
        Timber.d("======== revokeOldKey: ${k2.info()}")

        val revokeOldKeysMessage =
            ConfigKeyRefreshPhaseSet(k2, NetworkKey.REVOKE_OLD_KEYS)
        val e3 =
            nodes.traverseEither {
                bearer.setConfigKeyRefreshPhase(
                    it.unicastAddress.value,
                    revokeOldKeysMessage
                )
            }
                .bind()
        Timber.d("======== revokeOldKeysMessage: ${e3.map { it.isSuccessful }}")

        beckonMesh.networkKeys().map { Timber.d("New Net Key $it") }
        e2
    }

suspend fun Connected.appKeyRefresh(appKey: AppKey, nodes: List<Node>): Either<Any, Any> =
    either {

        val actualAppKey = beckonMesh.appKey(appKey.index)!!
        val newAppKey = MeshParserUtils.toByteArray(SecureUtils.generateRandomApplicationKey())
        Timber.d("======== Current App Key: ${actualAppKey.info()}")
        Timber.d("======== New App Key: ${newAppKey.toHex()}")

        val updatedAppKey = distributeAppKey(actualAppKey, newAppKey).bind()
        Timber.d("======== updatedAppKey: ${updatedAppKey.info()}")

        val updateAppKeyMessage = ConfigAppKeyUpdate(updatedAppKey)
        val e1 =
            nodes.traverseEither {
                bearer.updateConfigAppKey(
                    it.unicastAddress.value,
                    updateAppKeyMessage
                )
            }.bind()
        beckonMesh.appKeys().map { Timber.d("New App Key $it") }
        e1
    }
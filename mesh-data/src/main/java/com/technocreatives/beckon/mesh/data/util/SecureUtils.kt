package com.technocreatives.beckon.mesh.data.util


import com.technocreatives.beckon.mesh.data.Key
import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.engines.AESLightEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.modes.CCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.security.SecureRandom
import kotlin.experimental.and

object SecureUtils {
    /**
     * Used to calculate the confirmation key
     */
    val PRCK = "prck".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Used to calculate the session key
     */
    val PRSK = "prsk".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Used to calculate the session nonce
     */
    val PRSN = "prsn".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Used to calculate the device key
     */
    val PRDK = "prdk".toByteArray(Charset.forName("US-ASCII"))

    /**
     * K2 Master input
     */
    val K2_MASTER_INPUT = byteArrayOf(0x00)

    /**
     * Salt input for K2
     */
    val SMK2 = "smk2".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Salt input for K3
     */
    val SMK3 = "smk3".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Input for K3 data
     */
    val SMK3_DATA = "id64".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Output mask for K4
     */
    const val ENC_K3_OUTPUT_MASK = 0x7f

    /**
     * Salt input for K4
     */
    val SMK4 = "smk4".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Input for K4 data
     */
    val SMK4_DATA = "id6".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Output mask for K4
     */
    const val ENC_K4_OUTPUT_MASK = 0x3f

    //For S1, the key is constant
    internal val SALT_KEY = byteArrayOf(
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00
    )

    //Padding for the random nonce
    internal val NONCE_PADDING = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val TAG = SecureUtils::class.java.simpleName

    /**
     * Salt input for identity key
     */
    private val NKIK = "nkik".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Salt input for beacon key
     */
    private val NKBK = "nkbk".toByteArray(Charset.forName("US-ASCII"))

    /**
     * Salt input for identity key
     */
    private val ID128 = "id128".toByteArray(Charset.forName("US-ASCII"))

    //Padding for the random nonce
    private val HASH_PADDING = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private const val HASH_LENGTH = 8
    var NRF_MESH_KEY_SIZE = 16
    fun generateRandomNumber(): ByteArray {
        val random = SecureRandom()
        val randomBytes = ByteArray(16)
        random.nextBytes(randomBytes)
        return randomBytes
    }

    fun calculateSalt(data: ByteArray): ByteArray {
        return calculateCMAC(data, SALT_KEY)
    }

    fun calculateCMAC(data: ByteArray, key: ByteArray): ByteArray {
        val cmac = ByteArray(16)
        val cipherParameters: CipherParameters = KeyParameter(key)
        val blockCipher: BlockCipher = AESEngine()
        val mac = CMac(blockCipher)
        mac.init(cipherParameters)
        mac.update(data, 0, data.size)
        mac.doFinal(cmac, 0)
        return cmac
    }

    fun encryptCCM(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        micSize: Int
    ): ByteArray? {
        val ccm = ByteArray(data.size + micSize)
        val ccmBlockCipher = CCMBlockCipher(AESEngine())
        val aeadParameters = AEADParameters(KeyParameter(key), micSize * 8, nonce)
        ccmBlockCipher.init(true, aeadParameters)
        ccmBlockCipher.processBytes(data, 0, data.size, ccm, data.size)
        return try {
            ccmBlockCipher.doFinal(ccm, 0)
            ccm
        } catch (e: InvalidCipherTextException) {
            null
        }
    }

    fun encryptCCM(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        additionalData: ByteArray,
        micSize: Int
    ): ByteArray? {
        val ccm = ByteArray(data.size + micSize)
        val ccmBlockCipher = CCMBlockCipher(AESEngine())
        val aeadParameters = AEADParameters(KeyParameter(key), micSize * 8, nonce, additionalData)
        ccmBlockCipher.init(true, aeadParameters)
        ccmBlockCipher.processBytes(data, 0, data.size, ccm, data.size)
        return try {
            ccmBlockCipher.doFinal(ccm, 0)
            ccm
        } catch (e: InvalidCipherTextException) {
            null
        }
    }

    @Throws(InvalidCipherTextException::class)
    fun decryptCCM(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        micSize: Int
    ): ByteArray {
        val ccm = ByteArray(data.size - micSize)
        val ccmBlockCipher = CCMBlockCipher(AESEngine())
        val aeadParameters = AEADParameters(KeyParameter(key), micSize * 8, nonce)
        ccmBlockCipher.init(false, aeadParameters)
        ccmBlockCipher.processBytes(data, 0, data.size, ccm, 0)
        ccmBlockCipher.doFinal(ccm, 0)
        return ccm
    }

    @Throws(InvalidCipherTextException::class)
    fun decryptCCM(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        additionalData: ByteArray,
        micSize: Int
    ): ByteArray {
        val ccm = ByteArray(data.size - micSize)
        val ccmBlockCipher = CCMBlockCipher(AESEngine())
        val aeadParameters = AEADParameters(KeyParameter(key), micSize * 8, nonce, additionalData)
        ccmBlockCipher.init(false, aeadParameters)
        ccmBlockCipher.processBytes(data, 0, data.size, ccm, 0)
        ccmBlockCipher.doFinal(ccm, 0)
        return ccm
    }

    fun calculateK1(ecdh: ByteArray, confirmationSalt: ByteArray, text: ByteArray): ByteArray {
        return calculateCMAC(text, calculateCMAC(ecdh, confirmationSalt))
    }

    /**
     * Calculate k2
     *
     * @param data network key
     * @param p    master input
     */
    fun calculateK2(data: ByteArray, p: ByteArray): K2Output {
        val salt = calculateSalt(SMK2)
        val t = calculateCMAC(data, salt)
        val t0 = byteArrayOf()
        val inputBufferT0 = ByteBuffer.allocate(t0.size + p.size + 1)
        inputBufferT0.put(t0)
        inputBufferT0.put(p)
        inputBufferT0.put(0x01.toByte())
        val t1 = calculateCMAC(inputBufferT0.array(), t)
        val nid: Byte = t1[15] and 0x7F
        val inputBufferT1 = ByteBuffer.allocate(t1.size + p.size + 1)
        inputBufferT1.put(t1)
        inputBufferT1.put(p)
        inputBufferT1.put(0x02.toByte())
        val encryptionKey = calculateCMAC(inputBufferT1.array(), t)
        val inputBufferT2 = ByteBuffer.allocate(encryptionKey.size + p.size + 1)
        inputBufferT2.put(encryptionKey)
        inputBufferT2.put(p)
        inputBufferT2.put(0x03.toByte())
        val privacyKey = calculateCMAC(inputBufferT2.array(), t)
        return K2Output(nid, encryptionKey, privacyKey)
    }

    /**
     * Calculate k3
     *
     * @param n network key
     */
    fun calculateK3(n: ByteArray): ByteArray {
        val salt = calculateSalt(SMK3)
        val t = calculateCMAC(n, salt)
        val buffer = ByteBuffer.allocate(SMK3_DATA.size + 1)
        buffer.put(SMK3_DATA)
        buffer.put(0x01.toByte())
        val cmacInput = buffer.array()
        val result = calculateCMAC(cmacInput, t)

        //Only the least significant 8 bytes are returned
        val networkId = ByteArray(8)
        val srcOffset = result.size - networkId.size
        System.arraycopy(result, srcOffset, networkId, 0, networkId.size)
        return networkId
    }

    /**
     * Calculate k4
     *
     * @param n key
     */
    fun calculateK4(n: ByteArray?): Byte {
        require(!(n == null || n.size != 16)) { "Key cannot be empty and must be 16-bytes long." }
        val salt = calculateSalt(SMK4)
        val t = calculateCMAC(n, salt)
        val buffer = ByteBuffer.allocate(SMK4_DATA.size + 1)
        buffer.put(SMK4_DATA)
        buffer.put(0x01.toByte())
        val cmacInput = buffer.array()
        val result = calculateCMAC(cmacInput, t)

        //Only the least siginificant 6 bytes are returned
        return result[15] and 0x3F
    }

    /**
     * Calculates the identity key
     *
     * @param n network key
     * @return hash value
     */
    fun calculateIdentityKey(key: Key): Key {
        val salt = calculateSalt(NKIK)
        val buffer = ByteBuffer.allocate(ID128.size + 1)
        buffer.put(ID128)
        buffer.put(0x01.toByte())
        val p = buffer.array()
        return Key(calculateK1(key.value, salt, p))
    }

    /**
     * Calculates the beacon key
     *
     * @param n network key
     * @return hash value
     */
    fun calculateBeaconKey(n: ByteArray): ByteArray {
        val salt = calculateSalt(NKBK)
        val buffer = ByteBuffer.allocate(ID128.size + 1)
        buffer.put(ID128)
        buffer.put(0x01.toByte())
        val p = buffer.array()
        return calculateK1(n, salt, p)
    }

    /**
     * Calculates the authentication value of secure network beacon
     *
     * @param n         network key
     * @param flags     flags
     * @param networkId network id of the network
     * @param ivIndex   ivindex of the network
     */
    fun calculateAuthValueSecureNetBeacon(
        n: ByteArray,
        flags: Int,
        networkId: ByteArray,
        ivIndex: Int
    ): ByteArray {
        val inputLength = 1 + networkId.size + 4
        val pBuffer = ByteBuffer.allocate(inputLength)
        pBuffer.put(flags.toByte())
        pBuffer.put(networkId)
        pBuffer.putInt(ivIndex)
        val beaconKey = calculateBeaconKey(n)
        return calculateCMAC(pBuffer.array(), beaconKey)
    }

    /**
     * Calculates the secure network beacon
     *
     * @param n         network key
     * @param flags     network flags, this represents the current state of hte network if key refresh/iv update is ongoing or complete
     * @param networkId unique id of the network
     * @param ivIndex   iv index of the network
     */
    fun calculateSecureNetworkBeacon(
        n: ByteArray,
        beaconType: Int,
        flags: Int,
        networkId: ByteArray,
        ivIndex: Int
    ): ByteArray {
        val authentication = calculateAuthValueSecureNetBeacon(n, flags, networkId, ivIndex)
        val inputLength = 1 + networkId.size + 4
        val pBuffer = ByteBuffer.allocate(inputLength)
        pBuffer.put(flags.toByte())
        pBuffer.put(networkId)
        pBuffer.putInt(ivIndex)
        val secNetBeaconBuffer = ByteBuffer.allocate(1 + inputLength + 8)
        secNetBeaconBuffer.put(beaconType.toByte())
        secNetBeaconBuffer.put(pBuffer.array())
        secNetBeaconBuffer.put(authentication, 0, 8)
        return secNetBeaconBuffer.array()
    }

    /**
     * Calculates hash value for advertising with node id
     *
     * @param identityKey resolving identity key
     * @param random      64-bit random value
     * @param src         unicast address of the node
     * @return hash value
     */
    fun calculateHash(identityKey: ByteArray, random: ByteArray, src: ByteArray): ByteArray {
        val length = HASH_PADDING.size + random.size + src.size
        val bufferHashInput = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN)
        bufferHashInput.put(HASH_PADDING)
        bufferHashInput.put(random)
        bufferHashInput.put(src)
        val hashInput = bufferHashInput.array()
        val hash = encryptWithAES(hashInput, identityKey)
        val buffer = ByteBuffer.allocate(HASH_LENGTH).order(ByteOrder.BIG_ENDIAN)
        buffer.put(hash, 8, HASH_LENGTH)
        return buffer.array()
    }

    fun encryptWithAES(data: ByteArray, key: ByteArray): ByteArray {
        val encrypted = ByteArray(data.size)
        val cipherParameters: CipherParameters = KeyParameter(key)
        val engine = AESLightEngine()
        engine.init(true, cipherParameters)
        engine.processBlock(data, 0, encrypted, 0)
        return encrypted
    }

    fun getNetMicLength(ctl: Int): Int {
        return if (ctl == 0) {
            4 //length;
        } else {
            8 //length
        }
    }

    /**
     * Gets the transport MIC length based on the aszmic value
     *
     * @param aszmic application size message integrity check
     */
    fun getTransMicLength(aszmic: Int): Int {
        return if (aszmic == 0) {
            4 //length;
        } else {
            8 //length
        }
    }

    class K2Output {
        var nid: Byte
            private set

        var encryptionKey: ByteArray
            private set

        var privacyKey: ByteArray
            private set

        constructor(nid: Byte, encryptionKey: ByteArray, privacyKey: ByteArray) {
            this.nid = nid
            this.encryptionKey = encryptionKey
            this.privacyKey = privacyKey
        }

    }

}

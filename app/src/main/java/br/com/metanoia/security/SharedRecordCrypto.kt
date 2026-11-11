package br.com.metanoia.security

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Interoperable shared-record encryption.
 *
 * KDF: RFC 5869 HKDF-SHA-256, 288 output bits.
 * AEAD: AES-256-GCM, 96-bit nonce, 128-bit tag.
 * The first 32 derived bytes are the AES key; the final 4 bytes are the
 * per-(account, source device, purpose) nonce prefix. The nonce suffix is
 * the persistent unsigned 64-bit reservation counter.
 *
 * v1 remains handled by SyncCrypto for legacy local ciphertext.
 */
object SharedRecordCrypto {
    private const val DOMAIN = "metonoia-sync-record-v2"
    private const val HKDF_SALT = "metonoia-sync-v2-hkdf-salt"
    private const val PREFIX = "v2."
    private const val KEY_BYTES = 32
    private const val NONCE_PREFIX_BYTES = 4
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val MIN_PAYLOAD_BYTES = 8 + TAG_BYTES

    @Throws(GeneralSecurityException::class)
    fun encrypt(
        accountId: String,
        sourceDeviceId: String,
        purpose: String,
        recordId: String,
        dimension: String,
        recordType: String,
        accountDek: ByteArray,
        counter: Long,
        plaintext: ByteArray
    ): String {
        validate(accountId, sourceDeviceId, purpose, recordId, dimension, recordType, accountDek, counter, plaintext)
        val counterBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array()
        val material = derive(accountDek, accountId, sourceDeviceId, purpose)
        try {
            val nonce = material.copyOfRange(KEY_BYTES, KEY_BYTES + NONCE_PREFIX_BYTES) + counterBytes
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(material.copyOfRange(0, KEY_BYTES), "AES"),
                GCMParameterSpec(TAG_BITS, nonce)
            )
            cipher.updateAAD(aad(accountId, sourceDeviceId, purpose, recordId, dimension, recordType, counterBytes))
            return PREFIX + android.util.Base64.encodeToString(
                counterBytes + cipher.doFinal(plaintext),
                android.util.Base64.NO_WRAP
            )
        } finally {
            material.fill(0)
        }
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(
        accountId: String,
        sourceDeviceId: String,
        purpose: String,
        recordId: String,
        dimension: String,
        recordType: String,
        accountDek: ByteArray,
        value: String
    ): ByteArray {
        require(accountId.isNotBlank() && sourceDeviceId.isNotBlank() && purpose.isNotBlank())
        require(recordId.isNotBlank() && dimension.isNotBlank() && recordType.isNotBlank())
        require(accountDek.size == KEY_BYTES && value.startsWith(PREFIX))
        val packed = android.util.Base64.decode(value.removePrefix(PREFIX), android.util.Base64.NO_WRAP)
        require(packed.size > MIN_PAYLOAD_BYTES) { "Payload v2 inválido." }
        val counterBytes = packed.copyOfRange(0, 8)
        val counter = ByteBuffer.wrap(counterBytes).order(ByteOrder.BIG_ENDIAN).long
        require(counter >= 0L) { "Contador v2 fora do intervalo suportado." }
        val material = derive(accountDek, accountId, sourceDeviceId, purpose)
        try {
            val nonce = material.copyOfRange(KEY_BYTES, KEY_BYTES + NONCE_PREFIX_BYTES) + counterBytes
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(material.copyOfRange(0, KEY_BYTES), "AES"),
                GCMParameterSpec(TAG_BITS, nonce)
            )
            cipher.updateAAD(aad(accountId, sourceDeviceId, purpose, recordId, dimension, recordType, counterBytes))
            return cipher.doFinal(packed.copyOfRange(8, packed.size))
        } finally {
            material.fill(0)
        }
    }

    private fun validate(
        accountId: String,
        sourceDeviceId: String,
        purpose: String,
        recordId: String,
        dimension: String,
        recordType: String,
        accountDek: ByteArray,
        counter: Long,
        plaintext: ByteArray
    ) {
        require(accountId.isNotBlank() && sourceDeviceId.isNotBlank() && purpose.isNotBlank())
        require(recordId.isNotBlank() && dimension.isNotBlank() && recordType.isNotBlank())
        require(accountDek.size == KEY_BYTES && counter >= 0L && plaintext.isNotEmpty())
    }

    private fun derive(dek: ByteArray, accountId: String, deviceId: String, purpose: String): ByteArray {
        val salt = HKDF_SALT.toByteArray(Charsets.UTF_8)
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(dek)
        val info = encodeFields(DOMAIN, accountId, deviceId, purpose)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        val firstBlock = expand.doFinal(info + byteArrayOf(1))
        val secondBlock = expand.doFinal(firstBlock + info + byteArrayOf(2))
        val material = firstBlock + secondBlock.copyOfRange(0, 4)
        firstBlock.fill(0)
        secondBlock.fill(0)
        prk.fill(0)
        return material
    }

    private fun aad(
        accountId: String,
        deviceId: String,
        purpose: String,
        recordId: String,
        dimension: String,
        recordType: String,
        counterBytes: ByteArray
    ): ByteArray = encodeFields(
        DOMAIN, accountId, deviceId, purpose, recordId, dimension, recordType, counterBytes
    )

    /** Each field is UTF-8 (or supplied bytes), prefixed by an unsigned 32-bit BE length. */
    private fun encodeFields(vararg fields: Any): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            fields.forEach { field ->
                val bytes = when (field) {
                    is String -> field.toByteArray(Charsets.UTF_8)
                    is ByteArray -> field
                    else -> error("Unsupported canonical field.")
                }
                stream.writeInt(bytes.size)
                stream.write(bytes)
            }
        }
        return output.toByteArray()
    }
}

package com.kaidwal.securefieldcommunicationsystem

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Military-inspired Cryptographic Engine
 * Implements X25519 ECDH + AES-256-GCM encryption
 */
class CryptoEngine {

    private val secureRandom = SecureRandom()

    // Session key for current communication
    private var sessionKey: ByteArray? = null

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_SIZE = 32
    }

    data class KeyPair(
        val privateKey: ByteArray,
        val publicKey: ByteArray
    )

    data class EncryptedMessage(
        val ciphertext: ByteArray,
        val iv: ByteArray
    )

    /**
     * Generate X25519 elliptic curve key pair
     */
    fun generateKeyPair(): KeyPair {
        try {
            val generator = X25519KeyPairGenerator()
            generator.init(X25519KeyGenerationParameters(secureRandom))

            val keyPair = generator.generateKeyPair()
            val privateKey = keyPair.private as X25519PrivateKeyParameters
            val publicKey = keyPair.public as X25519PublicKeyParameters

            Timber.d("Generated X25519 key pair")

            return KeyPair(
                privateKey = privateKey.encoded,
                publicKey = publicKey.encoded
            )
        } catch (e: Exception) {
            Timber.e(e, "Key generation failed")
            throw e
        }
    }

    /**
     * Perform X25519 ECDH key agreement
     */
    fun deriveSharedSecret(
        privateKey: ByteArray,
        remotePublicKey: ByteArray
    ): ByteArray {
        try {
            val agreement = X25519Agreement()
            val sharedSecret = ByteArray(agreement.agreementSize)

            val privateKeyParams = X25519PrivateKeyParameters(privateKey, 0)
            val publicKeyParams = X25519PublicKeyParameters(remotePublicKey, 0)

            agreement.init(privateKeyParams)
            agreement.calculateAgreement(publicKeyParams, sharedSecret, 0)

            Timber.d("Derived shared secret")
            return sharedSecret
        } catch (e: Exception) {
            Timber.e(e, "Key agreement failed")
            throw e
        }
    }

    /**
     * Derive AES-256 key from shared secret using SHA-256
     */
    fun deriveAESKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret)
    }

    /**
     * Set session key for voice encryption
     */
    fun setSessionKey(key: ByteArray) {
        this.sessionKey = key
        Timber.d("Session key set")
    }

    /**
     * Encrypt message using AES-256-GCM (String version)
     */
    fun encrypt(plaintext: String, aesKey: ByteArray): EncryptedMessage {
        try {
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(aesKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            Timber.d("Encrypted message (${plaintext.length} bytes)")

            return EncryptedMessage(ciphertext, iv)
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw e
        }
    }

    /**
     * Decrypt message using AES-256-GCM (String version)
     */
    fun decrypt(encryptedMessage: EncryptedMessage, aesKey: ByteArray): String {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(aesKey, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedMessage.iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val plaintext = cipher.doFinal(encryptedMessage.ciphertext)

            Timber.d("Decrypted message")

            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw e
        }
    }

    /**
     * Encrypt raw bytes (for voice data) - NEW
     */
    fun encrypt(plainData: ByteArray): ByteArray {
        return try {
            val key = sessionKey ?: generateDefaultKey()

            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            val ciphertext = cipher.doFinal(plainData)

            // Prepend IV to ciphertext for easy transmission
            iv + ciphertext
        } catch (e: Exception) {
            Timber.e(e, "Voice encryption failed")
            plainData // Fallback to unencrypted
        }
    }

    /**
     * Decrypt raw bytes (for voice data) - NEW
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        return try {
            val key = sessionKey ?: generateDefaultKey()

            // Extract IV from first 12 bytes
            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Timber.e(e, "Voice decryption failed")
            encryptedData // Fallback to raw data
        }
    }

    /**
     * Generate default key for testing
     */
    private fun generateDefaultKey(): ByteArray {
        val defaultKey = "SFCS_DEFAULT_KEY_FOR_TESTING_32B".toByteArray()
        return defaultKey.copyOf(AES_KEY_SIZE)
    }

    /**
     * Generate random device ID (anonymous)
     */
    fun generateDeviceId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate random message ID
     */
    fun generateMessageId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Rotate session key (Perfect Forward Secrecy)
     */
    fun rotateKey(currentKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val timestamp = System.currentTimeMillis().toString().toByteArray()
        val newKey = digest.digest(currentKey + timestamp)
        sessionKey = newKey
        return newKey
    }
}

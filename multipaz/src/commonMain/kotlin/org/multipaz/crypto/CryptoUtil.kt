package org.multipaz.crypto

internal fun checkAesGcmKeySize(
    algorithm: Algorithm,
    key: ByteArray
) {
    val expectedSize = when (algorithm) {
        Algorithm.A128GCM -> 16
        Algorithm.A192GCM -> 24
        Algorithm.A256GCM -> 32
        else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
    }
    require(key.size == expectedSize) {
        "Key size for $algorithm must be $expectedSize bytes, got ${key.size}"
    }
}

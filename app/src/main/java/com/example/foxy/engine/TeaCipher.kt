package com.example.foxy.engine

import java.util.Arrays

/**
 * TEA Cipher — Tiny Encryption Algorithm, CBC mode, 16 rounds.
 * Dùng để decrypt body của game UDP packets.
 */
object TeaCipher {

    private const val DELTA = -0x61C88647  // 0x9E3779B9 unsigned

    fun encryptBlock(key: IntArray, input: ByteArray, output: ByteArray) {
        var v0 = readIntLE(input, 0)
        var v1 = readIntLE(input, 4)
        var sum = 0
        repeat(16) {
            // Gốc: i -= 1640531527 = sum += DELTA (DELTA = -1640531527)
            sum += DELTA
            v0 += ((v1 shl 4) + key[0]) xor (v1 + sum) xor ((v1 ushr 5) + key[1])
            v1 += ((v0 shl 4) + key[2]) xor (v0 + sum) xor ((v0 ushr 5) + key[3])
        }
        writeIntLE(output, 0, v0)
        writeIntLE(output, 4, v1)
    }

    fun decryptBlock(key: IntArray, input: ByteArray, output: ByteArray) {
        var v0 = readIntLE(input, 0)
        var v1 = readIntLE(input, 4)
        // Gốc: int i = -478700656 = DELTA * 16 (vì DELTA=-1640531527, DELTA*16 mod 2^32 = -478700656)
        var sum = DELTA * 16
        repeat(16) {
            v1 -= ((v0 shl 4) + key[2]) xor (v0 + sum) xor ((v0 ushr 5) + key[3])
            v0 -= ((v1 shl 4) + key[0]) xor (v1 + sum) xor ((v1 ushr 5) + key[1])
            // Gốc: i += 1640531527 = sum -= DELTA (vì DELTA=-1640531527)
            sum -= DELTA
        }
        writeIntLE(output, 0, v0)
        writeIntLE(output, 4, v1)
    }

    fun expandKey(keyBytes: ByteArray): IntArray = IntArray(4) { i -> readIntLE(keyBytes, i * 4) }

    /** CBC decrypt. Returns null if invalid. */
    fun decryptCbc(ciphertext: ByteArray, keyBytes: ByteArray): ByteArray? {
        if (ciphertext.size < 16 || ciphertext.size % 8 != 0) return null
        if (keyBytes.size < 16) return null
        val key = expandKey(keyBytes)
        val plain = ByteArray(ciphertext.size)
        val prevC = ByteArray(8)
        val prevP = ByteArray(8)
        val blockIn = ByteArray(8)
        val blockOut = ByteArray(8)
        var i = 0
        while (i < ciphertext.size) {
            for (j in 0..7) blockIn[j] = (ciphertext[i + j].toInt() xor prevC[j].toInt()).toByte()
            decryptBlock(key, blockIn, blockOut)
            for (j in 0..7) plain[i + j] = (blockOut[j].toInt() xor prevP[j].toInt()).toByte()
            System.arraycopy(blockOut, 0, prevC, 0, 8)
            System.arraycopy(ciphertext, i, prevP, 0, 8)
            i += 8
        }
        val paddingLen = (plain[0].toInt() and 7) + 3
        val dataEnd = plain.size - 7
        if (paddingLen < 0 || dataEnd < paddingLen || dataEnd > plain.size) return null
        for (k in dataEnd until plain.size) if (plain[k] != 0.toByte()) return null
        return plain.copyOfRange(paddingLen, dataEnd)
    }

    /** CBC encrypt. */
    fun encryptCbc(plaintext: ByteArray, keyBytes: ByteArray): ByteArray? {
        if (keyBytes.size < 16) return null
        val key = expandKey(keyBytes)
        val paddingLen = (8 - ((plaintext.size + 10) % 8)) % 8
        val totalLen = plaintext.size + paddingLen + 10
        val padded = ByteArray(totalLen)
        padded[0] = (paddingLen and 7).toByte()
        System.arraycopy(plaintext, 0, padded, paddingLen + 3, plaintext.size)
        val cipher = ByteArray(totalLen)
        val prevC = ByteArray(8)
        val prevP = ByteArray(8)
        val blockIn = ByteArray(8)
        val blockOut = ByteArray(8)
        var i = 0
        while (i < totalLen) {
            for (j in 0..7) blockIn[j] = (padded[i + j].toInt() xor prevC[j].toInt()).toByte()
            encryptBlock(key, blockIn, blockOut)
            for (j in 0..7) cipher[i + j] = (blockOut[j].toInt() xor prevP[j].toInt()).toByte()
            System.arraycopy(blockIn, 0, prevP, 0, 8)
            System.arraycopy(cipher, i, prevC, 0, 8)
            i += 8
        }
        return cipher
    }

    private fun readIntLE(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
        ((b[i+1].toInt() and 0xFF) shl 8) or
        ((b[i+2].toInt() and 0xFF) shl 16) or
        ((b[i+3].toInt() and 0xFF) shl 24)

    private fun writeIntLE(b: ByteArray, i: Int, v: Int) {
        b[i]   = (v and 0xFF).toByte()
        b[i+1] = ((v ushr 8) and 0xFF).toByte()
        b[i+2] = ((v ushr 16) and 0xFF).toByte()
        b[i+3] = ((v ushr 24) and 0xFF).toByte()
    }

    /** Simple XOR checksum for packet header validation */
    fun headerChecksum(data: ByteArray, offset: Int, len: Int): Byte {
        var crc = 0
        for (i in (offset + 2) until (offset + len)) crc = crc xor (data[i].toInt() and 0xFF)
        return crc.toByte()
    }
}

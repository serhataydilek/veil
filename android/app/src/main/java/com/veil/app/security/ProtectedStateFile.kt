package com.veil.app.security

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException

internal interface ProtectedStateFile {
    fun exists(): Boolean
    @Throws(IOException::class)
    fun read(maximumLength: Int): ByteArray
    fun write(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

internal class AndroidAtomicProtectedStateFile(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
) : ProtectedStateFile {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, fileName))

    override fun exists(): Boolean = atomicFile.baseFile.exists()

    override fun read(maximumLength: Int): ByteArray {
        require(maximumLength >= 0)
        atomicFile.openRead().use { input ->
            // One additional byte distinguishes an oversized physical file without
            // allocating based on its untrusted on-disk length.
            val output = ByteArrayOutputStream(maximumLength + 1)
            val buffer = ByteArray(minOf(1024, maximumLength + 1))
            var remaining = maximumLength + 1
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
            return output.toByteArray()
        }
    }

    override fun write(bytes: ByteArray): Boolean {
        val stream = try {
            atomicFile.startWrite()
        } catch (_: IOException) {
            return false
        }
        return try {
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            true
        } catch (_: IOException) {
            atomicFile.failWrite(stream)
            false
        }
    }

    override fun delete(): Boolean {
        atomicFile.delete()
        return !atomicFile.baseFile.exists()
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "veil-local-state.v1"
    }
}

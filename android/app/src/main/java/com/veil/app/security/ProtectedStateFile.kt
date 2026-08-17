package com.veil.app.security

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException

internal interface ProtectedStateFile {
    fun exists(): Boolean
    @Throws(IOException::class)
    fun read(): ByteArray
    fun write(bytes: ByteArray): Boolean
    fun delete(): Boolean
}

internal class AndroidAtomicProtectedStateFile(
    context: Context,
    fileName: String = DEFAULT_FILE_NAME,
) : ProtectedStateFile {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, fileName))

    override fun exists(): Boolean = atomicFile.baseFile.exists()

    override fun read(): ByteArray = atomicFile.readFully()

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

package com.inkreader.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object FileUtils {
    fun calculateHash(file: File): String {
        return calculateHash(file.inputStream())
    }

    fun calculateHash(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        return calculateHash(inputStream!!)
    }

    private fun calculateHash(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        inputStream.use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

package com.example.snowybottext.web

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import java.io.File

/** Imports a user-selected JS file into app-private storage, avoiding external path assumptions. */
class SnowybotScriptStore(private val destination: File) {
    fun import(contentResolver: ContentResolver, source: Uri): File {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        try {
            contentResolver.openInputStream(source)?.use { input ->
                temporary.outputStream().use(input::copyTo)
            } ?: throw IllegalArgumentException("Unable to open the selected JavaScript file")
            require(temporary.length() > 0L) { "Selected JavaScript file is empty" }
            check(temporary.renameTo(destination) || temporary.copyTo(destination, overwrite = true).exists()) {
                "Unable to store imported JavaScript file"
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return destination
    }

    fun read(): String? = destination.takeIf(File::isFile)?.readText()

    fun exists(): Boolean = destination.isFile

    companion object {
        const val FILE_NAME = "snowybot.js"

        /** Bundled script is the default; user-imported file remains an optional override. */
        fun readBundled(assets: AssetManager): String =
            assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

}

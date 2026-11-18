package com.tarixustoz.app.data.remote

import com.tarixustoz.app.BuildConfig
import java.net.URI

/** Resolves backend media and migrates cached development URLs to production. */
object MediaUrls {
    private val origin = URI(BuildConfig.TARIX_API_BASE_URL).resolve("/")

    fun resolve(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value.replace(" ", "%20")) }.getOrNull() ?: return value
        val path = uri.rawPath.orEmpty().replace("/media/books/pdfs/", "/media/pdfs/")
        return when {
            uri.host in setOf("10.0.2.2", "127.0.0.1", "localhost") -> origin.resolve(path).toASCIIString()
            value.startsWith("/") -> origin.resolve(path).toASCIIString()
            else -> value
        }
    }

    fun textbook(grade: Int, subject: String): String {
        val name = when {
            grade == 6 -> "Qadimgi_dunyo_tarixi"
            subject.contains("jahon", ignoreCase = true) -> "Jahon_tarixi"
            else -> "Ozbekiston_tarixi"
        }
        return origin.resolve("media/pdfs/${grade}-sinf_$name.pdf").toASCIIString()
    }
}

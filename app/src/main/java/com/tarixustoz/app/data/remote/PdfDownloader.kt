package com.tarixustoz.app.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState() // 0 to 100
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object PdfDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getLocalPdfFile(context: Context, urlOrPath: String): File {
        val resolved = MediaUrls.resolve(urlOrPath) ?: urlOrPath
        val fileName = "pdf_${resolved.hashCode()}.pdf"
        return File(context.filesDir, fileName)
    }

    fun isPdfDownloaded(context: Context, urlOrPath: String): Boolean {
        if (!urlOrPath.startsWith("http")) return true
        val file = getLocalPdfFile(context, urlOrPath)
        return file.exists() && file.length() > 50000L
    }

    fun downloadPdf(context: Context, urlOrPath: String): Flow<DownloadState> = flow {
        val resolved = MediaUrls.resolve(urlOrPath) ?: urlOrPath
        val targetFile = getLocalPdfFile(context, resolved)

        if (targetFile.exists() && targetFile.length() > 50000L) {
            emit(DownloadState.Success(targetFile))
            return@flow
        }

        emit(DownloadState.Downloading(0))

        try {
            val cleanUrl = resolved.replace(" ", "%20")
            val request = Request.Builder().url(cleanUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP ${response.code}: ${response.message}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(DownloadState.Error("Serverdan bo'sh javob qaytdi"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    var bytesRead: Int
                    var lastReportedProgress = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                            if (progress != lastReportedProgress) {
                                lastReportedProgress = progress
                                emit(DownloadState.Downloading(progress))
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.length() > 1000L) {
                emit(DownloadState.Downloading(100))
                emit(DownloadState.Success(targetFile))
            } else {
                targetFile.delete()
                emit(DownloadState.Error("Yuklab olingan fayl to'liq emas"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            targetFile.delete()
            emit(DownloadState.Error("Yuklab olishda xatolik: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)
}

package com.tarixustoz.app.presentation.textbooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.local.PdfAnnotationEntity
import com.tarixustoz.app.data.local.PdfBookmarkEntity
import com.tarixustoz.app.data.local.PdfProgressEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val db: AppDatabase
) : ViewModel() {

    fun getBookmarks(pdfPath: String): Flow<List<PdfBookmarkEntity>> =
        db.pdfBookmarkDao().getBookmarks(pdfPath)

    fun toggleBookmark(pdfPath: String, pageNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.pdfBookmarkDao().getBookmark(pdfPath, pageNumber)
            if (existing != null) {
                db.pdfBookmarkDao().delete(pdfPath, pageNumber)
            } else {
                val bookmark = PdfBookmarkEntity(
                    id = "${pdfPath}_${pageNumber}",
                    pdfPath = pdfPath,
                    pageNumber = pageNumber
                )
                db.pdfBookmarkDao().insert(bookmark)
            }
        }
    }

    suspend fun getSavedPage(pdfPath: String): Int {
        return db.pdfProgressDao().getProgress(pdfPath)?.lastPage ?: 1
    }

    fun saveProgress(pdfPath: String, pageNumber: Int, zoomScale: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val progress = PdfProgressEntity(
                pdfPath = pdfPath,
                lastPage = pageNumber,
                zoomScale = zoomScale,
                updatedAt = System.currentTimeMillis()
            )
            db.pdfProgressDao().saveProgress(progress)
        }
    }

    fun getAnnotationsForPage(pdfPath: String, pageNumber: Int): Flow<List<PdfAnnotationEntity>> =
        db.pdfAnnotationDao().getAnnotations(pdfPath, pageNumber)

    fun saveAnnotation(annotation: PdfAnnotationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pdfAnnotationDao().insert(annotation)
        }
    }

    fun clearPageAnnotations(pdfPath: String, pageNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.pdfAnnotationDao().clearPageAnnotations(pdfPath, pageNumber)
        }
    }
}

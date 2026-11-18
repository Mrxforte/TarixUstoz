package com.tarixustoz.app.data.remote

import org.junit.Assert.*
import org.junit.Test

class MediaUrlsTest {
    @Test fun replacesCachedEmulatorPdfWithProductionUrl() {
        assertEquals(MediaUrls.textbook(7, "jahon"),
            MediaUrls.resolve("http://10.0.2.2:8000/media/books/pdfs/7-sinf_Jahon_tarixi.pdf"))
    }

    @Test fun resolvesRelativePdfAndPreservesExternalLinks() {
        assertEquals(MediaUrls.textbook(6, "uzbekiston"),
            MediaUrls.resolve("/media/pdfs/6-sinf_Qadimgi_dunyo_tarixi.pdf"))
        assertEquals("https://cdn.example.org/book.pdf", MediaUrls.resolve("https://cdn.example.org/book.pdf"))
        assertNull(MediaUrls.resolve(" "))
    }

    @Test fun normalizesBookSubjectsForRoomFilters() {
        assertEquals("jahon", ApiPdfBookDto(id = 1, title = "World", subjectId = "Jahon tarixi").effectiveSubject)
        assertEquals("uzbekiston", ApiPdfBookDto(id = 2, title = "History", subjectId = "O'zbekiston tarixi").effectiveSubject)
    }
}

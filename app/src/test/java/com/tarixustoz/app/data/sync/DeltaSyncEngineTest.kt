package com.tarixustoz.app.data.sync

import com.google.common.truth.Truth.assertThat
import com.tarixustoz.app.data.local.TextbookEntity
import com.tarixustoz.app.data.local.TopicEntity
import com.tarixustoz.app.data.remote.ApiPdfBookDto
import com.tarixustoz.app.data.remote.DirectionCountDto
import com.tarixustoz.app.data.remote.DirectionsResponseDto
import org.junit.Test

class DeltaSyncEngineTest {

    private fun bookDto(
        id: Long,
        grade: Int,
        subject: String,
        title: String,
        topics: Int,
        questions: Int
    ) = ApiPdfBookDto(
        id = id,
        title = title,
        grade = grade,
        gradeId = grade,
        subject = subject,
        subjectId = subject,
        totalTopics = topics,
        totalQuestions = questions
    )

    @Test
    fun `diffBooksFingerprint detects zero changes when books match`() {
        val books = listOf(
            bookDto(1, 6, "uzbekiston", "Qadimgi dunyo", 42, 10000),
            bookDto(2, 7, "jahon", "Jahon tarixi", 39, 12000)
        )
        val fp = DeltaSyncEngine.computeBooksFingerprint(books)
        val diff = DeltaSyncEngine.diffBooksFingerprint(
            previousFingerprint = fp,
            previousTopicsTotal = 81,
            incomingBooks = books
        )

        assertThat(diff.hasAnyChange).isFalse()
        assertThat(diff.changedBookKeys).isEmpty()
        assertThat(diff.topicsCountChanged).isFalse()
    }

    @Test
    fun `diffBooksFingerprint selectively flags only modified book key`() {
        val initial = listOf(
            bookDto(1, 6, "uzbekiston", "Qadimgi dunyo", 42, 10000),
            bookDto(2, 7, "jahon", "Jahon tarixi", 39, 12000)
        )
        val updated = listOf(
            bookDto(1, 6, "uzbekiston", "Qadimgi dunyo", 42, 10000),
            bookDto(2, 7, "jahon", "Jahon tarixi", 40, 12150)
        )
        val fp = DeltaSyncEngine.computeBooksFingerprint(initial)
        val diff = DeltaSyncEngine.diffBooksFingerprint(
            previousFingerprint = fp,
            previousTopicsTotal = 81,
            incomingBooks = updated
        )

        assertThat(diff.hasAnyChange).isTrue()
        assertThat(diff.topicsCountChanged).isTrue()
        assertThat(diff.changedBookKeys).containsExactly("book_jahon_7", "jahon_7")
        assertThat(diff.totalRemoteTopics).isEqualTo(82)
    }

    @Test
    fun `diffDirectionsFingerprint invalidates only directions with changed counts`() {
        val first = DirectionsResponseDto(
            grade = 0,
            directions = listOf(
                DirectionCountDto("EVENT", "Voqealar", 100),
                DirectionCountDto("WAR", "Urushlar", 50),
                DirectionCountDto("PERSON", "Shaxslar", 75)
            )
        )
        val firstDiff = DeltaSyncEngine.diffDirectionsFingerprint(null, first)

        val second = DirectionsResponseDto(
            grade = 0,
            directions = listOf(
                DirectionCountDto("EVENT", "Voqealar", 100),
                DirectionCountDto("WAR", "Urushlar", 58),
                DirectionCountDto("PERSON", "Shaxslar", 75)
            )
        )
        val secondDiff = DeltaSyncEngine.diffDirectionsFingerprint(firstDiff.newFingerprint, second)

        assertThat(secondDiff.hasAnyChange).isTrue()
        assertThat(secondDiff.changedDirections).containsExactly("WAR")
    }

    @Test
    fun `diffTopics computes minimal upsert and prune without full wipe`() {
        val existing = listOf(
            TopicEntity("topic_1", "ch_1", "uz_6", 1, "Mavzu 1", "Body 1", gradeId = 6),
            TopicEntity("topic_2", "ch_1", "uz_6", 2, "Mavzu 2", "Body 2", gradeId = 6),
            TopicEntity("topic_3", "ch_1", "uz_6", 3, "O'chirilgan", "Body 3", gradeId = 6)
        )
        val incoming = listOf(
            TopicEntity("topic_1", "ch_1", "uz_6", 1, "Mavzu 1", "Body 1", gradeId = 6),
            TopicEntity("topic_2", "ch_1", "uz_6", 2, "Mavzu 2 (yangilangan)", "Body 2+", gradeId = 6),
            TopicEntity("topic_4", "ch_1", "uz_6", 4, "Yangi mavzu 4", "Body 4", gradeId = 6)
        )

        val fullDiff = DeltaSyncEngine.diffTopics(existing, incoming, pruneRemoved = true)
        assertThat(fullDiff.toUpsert.map { it.id }).containsExactly("topic_2", "topic_4")
        assertThat(fullDiff.toDeleteIds).containsExactly("topic_3")

        val cursorDiff = DeltaSyncEngine.diffTopics(existing, listOf(incoming.last()), pruneRemoved = false)
        assertThat(cursorDiff.toUpsert.map { it.id }).containsExactly("topic_4")
        assertThat(cursorDiff.toDeleteIds).isEmpty()
    }

    @Test
    fun `shouldInvalidateTicketKey targets only changed categories`() {
        val warKey = "https://tarix.1xbet-getapk.com/api/:ticket_WAR_1_30_0"
        val personKey = "https://tarix.1xbet-getapk.com/api/:ticket_PERSON_2_30_0"
        val bookKey = "https://tarix.1xbet-getapk.com/api/:ticket_book_jahon_7_1_30_7"

        assertThat(DeltaSyncEngine.shouldInvalidateTicketKey(warKey, setOf("WAR"))).isTrue()
        assertThat(DeltaSyncEngine.shouldInvalidateTicketKey(personKey, setOf("WAR"))).isFalse()
        assertThat(DeltaSyncEngine.shouldInvalidateTicketKey(bookKey, setOf("book_jahon_7"))).isTrue()
        assertThat(DeltaSyncEngine.shouldInvalidateTicketKey(bookKey, setOf("book_uz_7"))).isFalse()
    }

    @Test
    fun `diffTextbooks skips identical records`() {
        val books = listOf(
            TextbookEntity("uz_6", "Tarix 6", 6, "uzbekiston", "Authors", 2020, "1", 5, 42, 1000)
        )
        val diff = DeltaSyncEngine.diffTextbooks(books, books, pruneRemoved = true)
        assertThat(diff.hasChanges).isFalse()
    }
}

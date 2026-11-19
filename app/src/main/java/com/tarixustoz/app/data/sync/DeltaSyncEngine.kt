package com.tarixustoz.app.data.sync

import com.tarixustoz.app.data.local.TextbookEntity
import com.tarixustoz.app.data.local.TopicEntity
import com.tarixustoz.app.data.remote.ApiPdfBookDto
import com.tarixustoz.app.data.remote.DirectionsResponseDto

/**
 * Pure Kotlin delta-detection & entity diff planner used by [ContentRepository] and WorkManager.
 * Computes deterministic fingerprints, detects changed category/book buckets for selective cache
 * invalidation, and produces minimal Room upsert/delete sets instead of destructive table wipes.
 */
object DeltaSyncEngine {

    data class EntityDiff<T>(
        val toUpsert: List<T>,
        val toDeleteIds: List<String>
    ) {
        val hasChanges: Boolean get() = toUpsert.isNotEmpty() || toDeleteIds.isNotEmpty()
    }

    data class BooksFingerprintDiff(
        val newFingerprint: String,
        val changedBookKeys: Set<String>,
        val topicsCountChanged: Boolean,
        val totalRemoteTopics: Int
    ) {
        val hasAnyChange: Boolean get() = changedBookKeys.isNotEmpty() || topicsCountChanged
    }

    data class DirectionsFingerprintDiff(
        val newFingerprint: String,
        val changedDirections: Set<String>,
        val countsByCode: Map<String, Int>
    ) {
        val hasAnyChange: Boolean get() = changedDirections.isNotEmpty()
    }

    fun computeBooksFingerprint(books: List<ApiPdfBookDto>): String {
        if (books.isEmpty()) return ""
        return books
            .sortedBy { "${it.effectiveGrade}_${it.effectiveSubject}" }
            .joinToString("|") { b ->
                val bookId = if (b.effectiveSubject == "jahon") "jahon_${b.effectiveGrade}" else "uz_${b.effectiveGrade}"
                "$bookId:${b.title.hashCode()}:${b.totalTopics}:${b.totalQuestions}:${b.totalChapters}"
            }
    }

    fun diffBooksFingerprint(
        previousFingerprint: String?,
        previousTopicsTotal: Int,
        incomingBooks: List<ApiPdfBookDto>
    ): BooksFingerprintDiff {
        val newFp = computeBooksFingerprint(incomingBooks)
        val totalTopics = incomingBooks.sumOf { it.totalTopics.coerceAtLeast(0) }
        if (previousFingerprint.isNullOrBlank()) {
            val allBookKeys = incomingBooks.map { b ->
                val id = if (b.effectiveSubject == "jahon") "jahon_${b.effectiveGrade}" else "uz_${b.effectiveGrade}"
                "book_$id"
            }.toSet()
            return BooksFingerprintDiff(
                newFingerprint = newFp,
                changedBookKeys = allBookKeys,
                topicsCountChanged = true,
                totalRemoteTopics = totalTopics
            )
        }

        val prevParts = previousFingerprint.split("|")
            .filter { it.contains(":") }
            .associate { entry ->
                val idx = entry.indexOf(':')
                entry.substring(0, idx) to entry.substring(idx + 1)
            }

        val changed = mutableSetOf<String>()
        for (b in incomingBooks) {
            val bookId = if (b.effectiveSubject == "jahon") "jahon_${b.effectiveGrade}" else "uz_${b.effectiveGrade}"
            val currentSig = "${b.title.hashCode()}:${b.totalTopics}:${b.totalQuestions}:${b.totalChapters}"
            if (prevParts[bookId] != currentSig) {
                changed.add("book_$bookId")
                changed.add(bookId)
            }
        }
        // Any removed book also marks its cache stale.
        val incomingBookIds = incomingBooks.map { b ->
            if (b.effectiveSubject == "jahon") "jahon_${b.effectiveGrade}" else "uz_${b.effectiveGrade}"
        }.toSet()
        for (oldId in prevParts.keys - incomingBookIds) {
            changed.add("book_$oldId")
            changed.add(oldId)
        }

        return BooksFingerprintDiff(
            newFingerprint = newFp,
            changedBookKeys = changed,
            topicsCountChanged = previousTopicsTotal != totalTopics || changed.isNotEmpty(),
            totalRemoteTopics = totalTopics
        )
    }

    fun diffDirectionsFingerprint(
        previousFingerprint: String?,
        response: DirectionsResponseDto
    ): DirectionsFingerprintDiff {
        val countsMap = response.directions.associate { it.code to it.count }
        val newFp = countsMap.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}:${it.value}" }

        if (previousFingerprint.isNullOrBlank()) {
            return DirectionsFingerprintDiff(
                newFingerprint = newFp,
                changedDirections = countsMap.keys,
                countsByCode = countsMap
            )
        }

        val prevMap = previousFingerprint.split("|")
            .filter { it.contains(":") }
            .associate { entry ->
                val parts = entry.split(":")
                parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: -1)
            }

        val changed = mutableSetOf<String>()
        for ((code, count) in countsMap) {
            if (prevMap[code] != count) {
                changed.add(code)
            }
        }
        return DirectionsFingerprintDiff(
            newFingerprint = newFp,
            changedDirections = changed,
            countsByCode = countsMap
        )
    }

    fun diffTextbooks(
        existing: List<TextbookEntity>,
        incoming: List<TextbookEntity>,
        pruneRemoved: Boolean = true
    ): EntityDiff<TextbookEntity> {
        val existingMap = existing.associateBy { it.id }
        val toUpsert = incoming.filter { item -> existingMap[item.id] != item }
        val toDelete = if (pruneRemoved) {
            val incomingIds = incoming.map { it.id }.toSet()
            existingMap.keys.filterNot { it in incomingIds }
        } else {
            emptyList()
        }
        return EntityDiff(toUpsert = toUpsert, toDeleteIds = toDelete)
    }

    fun diffTopics(
        existing: List<TopicEntity>,
        incoming: List<TopicEntity>,
        pruneRemoved: Boolean
    ): EntityDiff<TopicEntity> {
        val existingMap = existing.associateBy { it.id }
        val toUpsert = incoming.filter { item -> existingMap[item.id] != item }
        val toDelete = if (pruneRemoved) {
            val incomingIds = incoming.map { it.id }.toSet()
            existingMap.keys.filterNot { it in incomingIds }
        } else {
            emptyList()
        }
        return EntityDiff(toUpsert = toUpsert, toDeleteIds = toDelete)
    }

    fun extractNumericId(prefixedId: String, prefix: String): Long? {
        return prefixedId.removePrefix(prefix).toLongOrNull()
    }

    fun maxNumericTopicId(topics: List<TopicEntity>): Long {
        return topics.mapNotNull { extractNumericId(it.id, "topic_") }.maxOrNull() ?: 0L
    }

    fun shouldInvalidateTicketKey(cacheKey: String, invalidatedCategories: Set<String>): Boolean {
        if (invalidatedCategories.isEmpty()) return false
        return invalidatedCategories.any { cat ->
            val clean = cat.trim()
            clean.isNotBlank() && (
                cacheKey.contains(":ticket_${clean}_") ||
                    cacheKey.contains(":ticket_book_${clean}_") ||
                    (clean.startsWith("book_") && cacheKey.contains(":ticket_${clean.removePrefix("book_")}_"))
                )
        }
    }
}

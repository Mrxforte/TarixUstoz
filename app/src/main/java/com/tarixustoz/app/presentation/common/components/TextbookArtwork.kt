package com.tarixustoz.app.presentation.common.components

import androidx.annotation.DrawableRes
import com.tarixustoz.app.R

/**
 * Returns a thematic historical illustration drawable resource matching a textbook,
 * grade/subject key, direction code, or topic.
 */
@DrawableRes
fun backgroundResForBook(bookOrKey: String, gradeId: Int = 0, subjectId: String = ""): Int {
    val key = bookOrKey.lowercase()
    val resolvedGrade = when {
        gradeId in 6..11 -> gradeId
        else -> Regex("\\d+").find(key)?.value?.toIntOrNull() ?: 7
    }
    val isJahon = subjectId.contains("jahon", ignoreCase = true) || key.contains("jahon")

    return when (resolvedGrade) {
        6 -> R.drawable.img_book_uz_6
        7 -> if (isJahon) R.drawable.img_book_jahon_7 else R.drawable.img_bg_medieval_uzbekistan
        8 -> if (isJahon) R.drawable.img_book_jahon_8 else R.drawable.img_book_uz_8
        9 -> if (isJahon) R.drawable.img_book_jahon_9 else R.drawable.img_book_uz_9
        10 -> if (isJahon) R.drawable.img_book_jahon_10 else R.drawable.img_book_uz_10
        else -> if (isJahon) R.drawable.img_book_jahon_11 else R.drawable.img_bg_modern_era
    }
}

@DrawableRes
fun backgroundResForDirection(directionCode: String): Int = when (directionCode.uppercase()) {
    "WAR" -> R.drawable.img_dir_war
    "YEAR" -> R.drawable.img_dir_year
    "PERSON" -> R.drawable.img_dir_person
    "CITY" -> R.drawable.img_dir_city
    "EVENT" -> R.drawable.img_dir_event
    "FACT" -> R.drawable.img_bg_chronology_facts
    "LOGICAL" -> R.drawable.img_dir_logical
    else -> R.drawable.img_dir_event
}

@DrawableRes
fun backgroundResForTopic(
    topicTitle: String,
    textbookId: String,
    gradeId: Int = 0
): Int {
    val normalized = topicTitle.lowercase()
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('`', '\'')

    return when {
        listOf("urush", "jang", "yurish", "qo'zg'olon", "muhoraba", "bosqin", "mudofaa", "kurash", "istilo")
            .any { it in normalized } -> R.drawable.img_dir_war
        listOf("shahar", "poytaxt", "qal'a", "hudud", "voha")
            .any { it in normalized } -> R.drawable.img_dir_city
        listOf("rim", "yunon", "gretsiya", "afina", "sparta", "misr", "fir'avn", "bobil", "makedon")
            .any { it in normalized } -> R.drawable.img_book_uz_6
        listOf("shaxs", "hukmdor", "sarkarda", "alloma", "mutafakkir", "amir temur", "bobur", "jaloliddin", "spitamen")
            .any { it in normalized } -> R.drawable.img_dir_person
        listOf("xronolog", "taqvim", "yil", "sana")
            .any { it in normalized } -> R.drawable.img_dir_year
        else -> backgroundResForBook(textbookId, gradeId)
    }
}


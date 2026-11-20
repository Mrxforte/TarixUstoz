package com.tarixustoz.app

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tarixustoz.app.ads.AdMobManager
import com.tarixustoz.app.ads.AdProbeStatus
import com.tarixustoz.app.ads.findActivity
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.local.QuestionEntity
import com.tarixustoz.app.data.remote.ApiService
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.domain.model.Difficulty
import com.tarixustoz.app.domain.model.QuestionCategory
import com.tarixustoz.app.domain.model.QuestionType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StartupAndCrashSafetyRobolectricTest {

    @Test
    fun `application resources and FileProvider uri resolution succeed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(context.getString(R.string.app_name)).isEqualTo("Tarix Ustoz")

        val tempPdf = File(context.cacheDir, "pdf_cache_sample.pdf").apply {
            writeText("%PDF-1.4\n")
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempPdf
            )
            assertThat(uri.toString()).contains("pdf_cache_sample.pdf")
        } finally {
            tempPdf.delete()
        }
    }

    @Test
    fun `findActivity safely returns null on Application and ContextThemeWrapper`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val wrapped = ContextThemeWrapper(appContext, android.R.style.Theme_Material_Light)

        assertThat(appContext.findActivity()).isNull()
        assertThat(wrapped.findActivity()).isNull()
    }

    @Test
    fun `AdMobManager initAdMob and probeLiveAdUnits do not crash on non-Activity context`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        AdMobManager.initAdMob(appContext)
        AdMobManager.initAdMob(appContext)
        AdMobManager.probeLiveAdUnits(appContext)

        val state = AdMobManager.diagnostics.value
        assertThat(state.liveProbeStatus).isEqualTo(AdProbeStatus.LIVE_FAILED_USING_TEST)
        assertThat(state.isTestMode).isTrue()
    }

    @Test
    fun `ContentRepository safely maps unknown backend enum values from Room`() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val stubApi = Proxy.newProxyInstance(
                ApiService::class.java.classLoader,
                arrayOf(ApiService::class.java)
            ) { _, _, _ ->
                throw UnsupportedOperationException("Network disabled in unit test")
            } as ApiService

            val repo = ContentRepository(appContext, db, stubApi)
            db.questionDao().insertAll(
                listOf(
                    QuestionEntity(
                        id = "q_unknown_enums",
                        topicId = "topic_1",
                        textbookId = "uz_6",
                        gradeId = 6,
                        subjectId = "uzbekiston",
                        type = "FUTURE_BACKEND_TYPE",
                        difficulty = "SUPER_HARD_UNKNOWN",
                        category = "NEW_BACKEND_CATEGORY",
                        text = "Qadimgi Samarqand qachon tashkil topgan?",
                        optionsJson = """[{"id":"a","text":"Mil. avv. VIII asr","isCorrect":true}]""",
                        explanation = "Tarixiy manba asosida.",
                        historicalDate = null,
                        personality = null,
                        sourceReference = null,
                        pageHint = null
                    )
                )
            )

            val loaded = repo.getQuestionById("q_unknown_enums")
            assertThat(loaded).isNotNull()
            assertThat(loaded!!.type).isEqualTo(QuestionType.SINGLE_CHOICE)
            assertThat(loaded.difficulty).isEqualTo(Difficulty.MEDIUM)
            assertThat(loaded.category).isEqualTo(QuestionCategory.ALL)
            assertThat(loaded.options).hasSize(1)
        } finally {
            db.close()
        }
    }
}

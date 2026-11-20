package com.tarixustoz.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class AdProbeStatus {
    IDLE,
    TESTING_LIVE,
    LIVE_ACTIVE,
    LIVE_FAILED_USING_TEST
}

enum class AdSlotLoadState {
    IDLE,
    LOADING,
    LOADED_LIVE,
    LOADED_TEST,
    FAILED
}

data class AdMobDiagnosticsState(
    val sdkInitialized: Boolean = false,
    val adapterStatusSummary: String = "Kutilmoqda...",
    val isTestMode: Boolean = false,
    val liveProbeStatus: AdProbeStatus = AdProbeStatus.IDLE,
    val liveErrorSummary: String? = null,
    val heroBannerState: AdSlotLoadState = AdSlotLoadState.IDLE,
    val quizBannerState: AdSlotLoadState = AdSlotLoadState.IDLE,
    val rewardedState: AdSlotLoadState = AdSlotLoadState.IDLE,
    val activeBannerUnitId: String = AdMobManager.BANNER_AD_UNIT_ID,
    val lastRewardEarnedAtMs: Long = 0L,
    val lastUpdatedAtMs: Long = 0L
)

object AdMobManager {

    private const val TAG = "AdMobManager"
    private const val PREFS_NAME = "admob_prefs"
    private const val KEY_USE_TEST_MODE = "use_test_mode"
    private const val KEY_LIVE_ERROR = "live_error_summary"
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isInitStarted = AtomicBoolean(false)

    // Real AdMob Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-2360068229850342/6027022065"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-2360068229850342/4905512084"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2360068229850342/5075906629"

    // Test Ad Unit IDs provided by Google for verified Test Mode preview
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_ADAPTIVE_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    fun computeInlineAdaptiveHeightBucket(availableHeightDp: Int): Int {
        val clamped = availableHeightDp.coerceIn(50, 420)
        return if (clamped < 90) {
            clamped
        } else {
            ((clamped / 16) * 16).coerceIn(90, 420)
        }
    }

    fun describeAdaptiveBannerTier(widthDp: Int, heightDp: Int): String {
        val safeWidth = widthDp.coerceAtLeast(300)
        val safeHeight = heightDp.coerceAtLeast(50)
        return when {
            safeHeight >= 220 -> "Inline Adaptive XL • ${safeWidth}×${safeHeight}dp (Maksimal CPM)"
            safeHeight >= 130 -> "Inline Adaptive L • ${safeWidth}×${safeHeight}dp (Yuqori CPM)"
            safeHeight >= 90 -> "Inline Adaptive M • ${safeWidth}×${safeHeight}dp"
            else -> "Anchored Adaptive • ${safeWidth}×${safeHeight}dp"
        }
    }

    fun effectiveAdaptiveBannerUnitId(requestedUnitId: String, isTestMode: Boolean): String {
        return if (isTestMode) TEST_ADAPTIVE_BANNER_AD_UNIT_ID else requestedUnitId
    }

    @Composable
    fun FallbackPromoBanner(
        modifier: Modifier = Modifier,
        title: String = "TarixUstoz • Tarix Testlari 📚",
        subtitle: String = "BARCHA SINFLAR VA TEST BILETLARI BILAN DTM GA TAYYORLANING!"
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
            }
        }
    }

    private val _diagnostics = MutableStateFlow(AdMobDiagnosticsState())
    val diagnostics: StateFlow<AdMobDiagnosticsState> = _diagnostics.asStateFlow()

    @Volatile
    private var appPrefsContext: Context? = null

    fun initAdMob(context: Context) {
        val appContext = context.applicationContext ?: context
        appPrefsContext = appContext
        if (!isInitStarted.compareAndSet(false, true)) {
            return
        }
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persistedTestMode = prefs.getBoolean(KEY_USE_TEST_MODE, false)
        val persistedError = prefs.getString(KEY_LIVE_ERROR, null)

        _diagnostics.update {
            it.copy(
                isTestMode = persistedTestMode,
                liveProbeStatus = if (persistedTestMode && !persistedError.isNullOrBlank()) {
                    AdProbeStatus.LIVE_FAILED_USING_TEST
                } else {
                    it.liveProbeStatus
                },
                liveErrorSummary = persistedError,
                activeBannerUnitId = if (persistedTestMode) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }

        applyRequestConfiguration(persistedTestMode)

        initScope.launch {
            try {
                MobileAds.initialize(appContext) { initializationStatus ->
                    val readyCount = initializationStatus.adapterStatusMap.values.count {
                        it.initializationState == AdapterStatus.State.READY
                    }
                    val totalCount = initializationStatus.adapterStatusMap.size
                    val summary = "SDK Tayyor ($readyCount/$totalCount adapter)"
                    Log.d(TAG, "AdMob Initialized: ${initializationStatus.adapterStatusMap}")
                    _diagnostics.update {
                        it.copy(
                            sdkInitialized = true,
                            adapterStatusSummary = summary,
                            lastUpdatedAtMs = System.currentTimeMillis()
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize AdMob", t)
                _diagnostics.update {
                    it.copy(
                        sdkInitialized = false,
                        adapterStatusSummary = "SDK xatosi: ${t.localizedMessage ?: "noma'lum"}",
                        lastUpdatedAtMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun applyRequestConfiguration(testMode: Boolean) {
        try {
            val testDeviceIds = if (testMode) {
                listOf(AdRequest.DEVICE_ID_EMULATOR)
            } else {
                emptyList()
            }
            val config = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(config)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to set RequestConfiguration", t)
        }
    }

    fun setTestModeEnabled(enabled: Boolean, reason: String? = null) {
        applyRequestConfiguration(enabled)
        appPrefsContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()?.apply {
            putBoolean(KEY_USE_TEST_MODE, enabled)
            if (reason != null) putString(KEY_LIVE_ERROR, reason) else if (!enabled) remove(KEY_LIVE_ERROR)
            apply()
        }
        _diagnostics.update {
            it.copy(
                isTestMode = enabled,
                liveProbeStatus = when {
                    enabled && !reason.isNullOrBlank() -> AdProbeStatus.LIVE_FAILED_USING_TEST
                    enabled -> AdProbeStatus.IDLE
                    else -> AdProbeStatus.TESTING_LIVE
                },
                liveErrorSummary = if (enabled) reason ?: it.liveErrorSummary else null,
                activeBannerUnitId = if (enabled) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun recordLiveFailureAndFallbackToTest(source: String, loadAdError: LoadAdError) {
        val errorText = "$source (kod ${loadAdError.code}): ${loadAdError.message}"
        Log.w(TAG, "Live AdMob failed -> switching & locking Test Mode. $errorText")
        setTestModeEnabled(enabled = true, reason = errorText)
    }

    fun recordLiveSuccess(source: String) {
        Log.d(TAG, "Live AdMob loaded cleanly on $source")
        appPrefsContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean(KEY_USE_TEST_MODE, false)
            ?.remove(KEY_LIVE_ERROR)
            ?.apply()
        _diagnostics.update {
            it.copy(
                isTestMode = false,
                liveProbeStatus = AdProbeStatus.LIVE_ACTIVE,
                liveErrorSummary = null,
                activeBannerUnitId = BANNER_AD_UNIT_ID,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * Actively tests the real AdMob unit IDs. If live units serve ads, switches to Live mode;
     * if live units fail (No Fill / account pending activation / emulator), records the exact
     * SDK error and leaves the app cleanly in Test Mode so real Google Test Ads render.
     */
    fun probeLiveAdUnits(context: Context) {
        val hostActivity = context.findActivity()
        if (hostActivity == null || hostActivity.isFinishing || hostActivity.isDestroyed) {
            setTestModeEnabled(
                enabled = true,
                reason = "Jonli tekshiruv uchun faol oyna topilmadi"
            )
            return
        }
        applyRequestConfiguration(false)
        _diagnostics.update {
            it.copy(
                liveProbeStatus = AdProbeStatus.TESTING_LIVE,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
        try {
            val probeView = AdView(hostActivity).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        recordLiveSuccess("Jonli Banner")
                        runCatching { destroy() }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        recordLiveFailureAndFallbackToTest("Jonli Banner", loadAdError)
                        runCatching { destroy() }
                    }
                }
            }
            probeView.loadAd(AdRequest.Builder().build())
        } catch (t: Throwable) {
            setTestModeEnabled(
                enabled = true,
                reason = "Jonli tekshiruv xatosi: ${t.localizedMessage ?: "noma'lum"}"
            )
        }
    }

    fun effectiveBannerUnitId(requestedUnitId: String, isTestMode: Boolean): String {
        return if (isTestMode) TEST_BANNER_AD_UNIT_ID else requestedUnitId
    }

    /**
     * Auto-refreshing Banner Ad view matching exact banner dimensions.
     * Automatically uses Test Banner in Test Mode or on live fallback.
     */
    @Composable
    fun BannerAdView(
        modifier: Modifier = Modifier,
        adUnitId: String = BANNER_AD_UNIT_ID,
        autoRefreshIntervalSeconds: Long = 30L
    ) {
        val diag by diagnostics.collectAsState()
        val resolvedUnitId = effectiveBannerUnitId(adUnitId, diag.isTestMode)

        var isAdLoaded by remember(resolvedUnitId) { mutableStateOf(false) }
        var isAdFailed by remember(resolvedUnitId) { mutableStateOf(false) }
        var localUnitId by remember(resolvedUnitId) { mutableStateOf(resolvedUnitId) }
        val adViewHolder = remember(localUnitId) { arrayOfNulls<AdView>(1) }

        LaunchedEffect(resolvedUnitId) {
            if (localUnitId != resolvedUnitId) {
                isAdLoaded = false
                isAdFailed = false
                localUnitId = resolvedUnitId
            }
        }

        LaunchedEffect(localUnitId) {
            while (isActive) {
                delay(autoRefreshIntervalSeconds * 1000L)
                runCatching { adViewHolder[0]?.loadAd(AdRequest.Builder().build()) }
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (!isAdFailed) {
                key(localUnitId) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            _diagnostics.update { it.copy(quizBannerState = AdSlotLoadState.LOADING) }
                            val hostContext = context.findActivity() ?: context
                            try {
                                AdView(hostContext).apply {
                                    setAdSize(AdSize.BANNER)
                                    setAdUnitId(localUnitId)
                                    adListener = object : AdListener() {
                                        override fun onAdLoaded() {
                                            isAdLoaded = true
                                            val isTest = localUnitId == TEST_BANNER_AD_UNIT_ID
                                            _diagnostics.update {
                                                it.copy(
                                                    quizBannerState = if (isTest) AdSlotLoadState.LOADED_TEST else AdSlotLoadState.LOADED_LIVE,
                                                    lastUpdatedAtMs = System.currentTimeMillis()
                                                )
                                            }
                                            if (!isTest) recordLiveSuccess("Banner")
                                        }

                                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                            Log.w(TAG, "Banner load failed for $localUnitId: ${loadAdError.message}")
                                            if (localUnitId != TEST_BANNER_AD_UNIT_ID) {
                                                recordLiveFailureAndFallbackToTest("Banner", loadAdError)
                                                isAdLoaded = false
                                                localUnitId = TEST_BANNER_AD_UNIT_ID
                                            } else {
                                                isAdFailed = true
                                                _diagnostics.update {
                                                    it.copy(
                                                        quizBannerState = AdSlotLoadState.FAILED,
                                                        lastUpdatedAtMs = System.currentTimeMillis()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    adViewHolder[0] = this
                                    loadAd(AdRequest.Builder().build())
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "BannerAdView creation failed", t)
                                isAdFailed = true
                                _diagnostics.update {
                                    it.copy(
                                        quizBannerState = AdSlotLoadState.FAILED,
                                        lastUpdatedAtMs = System.currentTimeMillis()
                                    )
                                }
                                View(context)
                            }
                        },
                        update = { view ->
                            if (view is AdView) {
                                adViewHolder[0] = view
                            }
                        }
                    )
                }
            }

            DisposableEffect(localUnitId) {
                onDispose {
                    runCatching { adViewHolder[0]?.destroy() }
                    adViewHolder[0] = null
                }
            }

            if (isAdFailed) {
                FallbackPromoBanner(
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                )
            } else if (!isAdLoaded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "Ad",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "AdMob Banner yuklanmoqda...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
            }
        }
    }

    /**
     * 100% Dedicated AdMob Banner Ad View for Hero Banner Slot (full-width ~350x250dp card).
     * Uses Inline Adaptive Banner matching the exact container width and 250dp height so there
     * are no empty gaps on the sides, top, or bottom.
     */
    @Composable
    fun DedicatedHeroBannerAdView(
        modifier: Modifier = Modifier,
        adUnitId: String = BANNER_AD_UNIT_ID,
        autoRefreshIntervalSeconds: Long = 30L
    ) {
        val diag by diagnostics.collectAsState()
        val resolvedUnitId = effectiveAdaptiveBannerUnitId(adUnitId, diag.isTestMode)

        var isAdLoaded by remember(resolvedUnitId) { mutableStateOf(false) }
        var isAdFailed by remember(resolvedUnitId) { mutableStateOf(false) }
        var localUnitId by remember(resolvedUnitId) { mutableStateOf(resolvedUnitId) }
        val adViewHolder = remember(localUnitId) { arrayOfNulls<AdView>(1) }

        LaunchedEffect(resolvedUnitId) {
            if (localUnitId != resolvedUnitId) {
                isAdLoaded = false
                isAdFailed = false
                localUnitId = resolvedUnitId
            }
        }

        LaunchedEffect(localUnitId) {
            while (isActive) {
                delay(autoRefreshIntervalSeconds * 1000L)
                runCatching { adViewHolder[0]?.loadAd(AdRequest.Builder().build()) }
            }
        }

        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val widthDpInt = maxWidth.value.takeIf { it.isFinite() }?.toInt()?.coerceIn(300, 1200) ?: 320
            val heightDpInt = maxHeight.value.takeIf { it.isFinite() }?.toInt()?.coerceIn(200, 260) ?: 250

            if (isAdFailed) {
                FallbackPromoBanner(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            } else {
                key(localUnitId, widthDpInt, heightDpInt) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            _diagnostics.update { it.copy(heroBannerState = AdSlotLoadState.LOADING) }
                            val hostContext = context.findActivity() ?: context
                            try {
                                val adSize = AdSize.getInlineAdaptiveBannerAdSize(widthDpInt, heightDpInt)
                                AdView(hostContext).apply {
                                    setAdSize(adSize)
                                    setAdUnitId(localUnitId)
                                    adListener = object : AdListener() {
                                        override fun onAdLoaded() {
                                            isAdLoaded = true
                                            val isTest = localUnitId == TEST_ADAPTIVE_BANNER_AD_UNIT_ID ||
                                                localUnitId == TEST_BANNER_AD_UNIT_ID
                                            _diagnostics.update {
                                                it.copy(
                                                    heroBannerState = if (isTest) AdSlotLoadState.LOADED_TEST else AdSlotLoadState.LOADED_LIVE,
                                                    lastUpdatedAtMs = System.currentTimeMillis()
                                                )
                                            }
                                            if (!isTest) recordLiveSuccess("Hero Banner")
                                        }

                                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                            Log.w(TAG, "Hero banner failed for $localUnitId: ${loadAdError.message}")
                                            when {
                                                localUnitId != TEST_ADAPTIVE_BANNER_AD_UNIT_ID &&
                                                    localUnitId != TEST_BANNER_AD_UNIT_ID -> {
                                                    recordLiveFailureAndFallbackToTest("Hero Banner", loadAdError)
                                                    isAdLoaded = false
                                                    localUnitId = TEST_ADAPTIVE_BANNER_AD_UNIT_ID
                                                }
                                                localUnitId == TEST_ADAPTIVE_BANNER_AD_UNIT_ID -> {
                                                    isAdLoaded = false
                                                    localUnitId = TEST_BANNER_AD_UNIT_ID
                                                }
                                                else -> {
                                                    isAdFailed = true
                                                    _diagnostics.update {
                                                        it.copy(
                                                            heroBannerState = AdSlotLoadState.FAILED,
                                                            lastUpdatedAtMs = System.currentTimeMillis()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    adViewHolder[0] = this
                                    loadAd(AdRequest.Builder().build())
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "DedicatedHeroBannerAdView creation failed", t)
                                isAdFailed = true
                                _diagnostics.update {
                                    it.copy(
                                        heroBannerState = AdSlotLoadState.FAILED,
                                        lastUpdatedAtMs = System.currentTimeMillis()
                                    )
                                }
                                View(context)
                            }
                        },
                        update = { view ->
                            if (view is AdView) {
                                adViewHolder[0] = view
                            }
                        }
                    )
                }
            }

            DisposableEffect(localUnitId, widthDpInt, heightDpInt) {
                onDispose {
                    runCatching { adViewHolder[0]?.destroy() }
                    adViewHolder[0] = null
                }
            }
        }
    }

    /**
     * Responsive Quiz Banner that dynamically fills the available vertical space between
     * the top quiz content and the "Javobni tekshirish" button / bottom bounds using Google's
     * Inline Adaptive Banner without any permanent debug/size overlay text.
     */
    @Composable
    fun ResponsiveQuizBannerAdView(
        modifier: Modifier = Modifier,
        adUnitId: String = BANNER_AD_UNIT_ID,
        autoRefreshIntervalSeconds: Long = 30L
    ) {
        val diag by diagnostics.collectAsState()
        val resolvedUnitId = effectiveAdaptiveBannerUnitId(adUnitId, diag.isTestMode)

        var isAdLoaded by remember(resolvedUnitId) { mutableStateOf(false) }
        var isAdFailed by remember(resolvedUnitId) { mutableStateOf(false) }
        var localUnitId by remember(resolvedUnitId) { mutableStateOf(resolvedUnitId) }
        val adViewHolder = remember(localUnitId) { arrayOfNulls<AdView>(1) }

        LaunchedEffect(resolvedUnitId) {
            if (localUnitId != resolvedUnitId) {
                isAdLoaded = false
                isAdFailed = false
                localUnitId = resolvedUnitId
            }
        }

        LaunchedEffect(localUnitId) {
            while (isActive) {
                delay(autoRefreshIntervalSeconds * 1000L)
                runCatching { adViewHolder[0]?.loadAd(AdRequest.Builder().build()) }
            }
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .testTag("quiz_adaptive_banner_box"),
            contentAlignment = Alignment.Center
        ) {
            val widthDpInt = maxWidth.value.takeIf { it.isFinite() }?.toInt()?.coerceIn(300, 1200) ?: 320
            val totalContainerHeightDp = maxHeight.value.takeIf { it.isFinite() }?.toInt()?.coerceIn(50, 420) ?: 100
            val heightBucketDp = computeInlineAdaptiveHeightBucket(totalContainerHeightDp)

            if (isAdFailed) {
                FallbackPromoBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp, max = 90.dp)
                )
            } else {
                key(localUnitId, widthDpInt, heightBucketDp) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            _diagnostics.update { it.copy(quizBannerState = AdSlotLoadState.LOADING) }
                            val hostContext = context.findActivity() ?: context
                            try {
                                val adaptiveSize = if (heightBucketDp >= 90) {
                                    AdSize.getInlineAdaptiveBannerAdSize(widthDpInt, heightBucketDp)
                                } else {
                                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(hostContext, widthDpInt)
                                }
                                AdView(hostContext).apply {
                                    setAdSize(adaptiveSize)
                                    setAdUnitId(localUnitId)
                                    adListener = object : AdListener() {
                                        override fun onAdLoaded() {
                                            isAdLoaded = true
                                            val isTest = localUnitId == TEST_ADAPTIVE_BANNER_AD_UNIT_ID ||
                                                localUnitId == TEST_BANNER_AD_UNIT_ID
                                            _diagnostics.update {
                                                it.copy(
                                                    quizBannerState = if (isTest) AdSlotLoadState.LOADED_TEST else AdSlotLoadState.LOADED_LIVE,
                                                    lastUpdatedAtMs = System.currentTimeMillis()
                                                )
                                            }
                                            if (!isTest) recordLiveSuccess("Test Adaptive Banner")
                                        }

                                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                            Log.w(TAG, "Responsive quiz banner failed for $localUnitId: ${loadAdError.message}")
                                            when {
                                                localUnitId != TEST_ADAPTIVE_BANNER_AD_UNIT_ID &&
                                                    localUnitId != TEST_BANNER_AD_UNIT_ID -> {
                                                    recordLiveFailureAndFallbackToTest("Test Adaptive Banner", loadAdError)
                                                    isAdLoaded = false
                                                    localUnitId = TEST_ADAPTIVE_BANNER_AD_UNIT_ID
                                                }
                                                localUnitId == TEST_ADAPTIVE_BANNER_AD_UNIT_ID -> {
                                                    isAdLoaded = false
                                                    localUnitId = TEST_BANNER_AD_UNIT_ID
                                                }
                                                else -> {
                                                    isAdFailed = true
                                                    _diagnostics.update {
                                                        it.copy(
                                                            quizBannerState = AdSlotLoadState.FAILED,
                                                            lastUpdatedAtMs = System.currentTimeMillis()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    adViewHolder[0] = this
                                    loadAd(AdRequest.Builder().build())
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "ResponsiveQuizBannerAdView creation failed", t)
                                isAdFailed = true
                                _diagnostics.update {
                                    it.copy(
                                        quizBannerState = AdSlotLoadState.FAILED,
                                        lastUpdatedAtMs = System.currentTimeMillis()
                                    )
                                }
                                View(context)
                            }
                        },
                        update = { view ->
                            if (view is AdView) {
                                adViewHolder[0] = view
                            }
                        }
                    )
                }
            }

            DisposableEffect(localUnitId, widthDpInt, heightBucketDp) {
                onDispose {
                    runCatching { adViewHolder[0]?.destroy() }
                    adViewHolder[0] = null
                }
            }
        }
    }

    /**
     * Loads and shows a Rewarded Video Ad.
     * Respects Test Mode; if a live unit fails, automatically switches to Test Mode and shows Google's Test Rewarded Ad.
     */
    fun showRewardedAd(
        activity: Activity,
        adUnitId: String? = null,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        val currentTestMode = _diagnostics.value.isTestMode
        val effectiveId = adUnitId ?: if (currentTestMode) TEST_REWARDED_AD_UNIT_ID else REWARDED_AD_UNIT_ID
        _diagnostics.update {
            it.copy(
                rewardedState = AdSlotLoadState.LOADING,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }

        val adRequest = AdRequest.Builder().build()
        try {
            RewardedAd.load(
                activity,
                effectiveId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        val isTest = effectiveId == TEST_REWARDED_AD_UNIT_ID
                        Log.d(TAG, "Rewarded ad loaded successfully (isTest=$isTest).")
                        _diagnostics.update {
                            it.copy(
                                rewardedState = if (isTest) AdSlotLoadState.LOADED_TEST else AdSlotLoadState.LOADED_LIVE,
                                lastUpdatedAtMs = System.currentTimeMillis()
                            )
                        }
                        if (!isTest) recordLiveSuccess("Mukofotli reklama")

                        if (!activity.isFinishing && !activity.isDestroyed) {
                            runCatching {
                                rewardedAd.show(activity) { rewardItem ->
                                    Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                                    _diagnostics.update {
                                        it.copy(
                                            lastRewardEarnedAtMs = System.currentTimeMillis(),
                                            lastUpdatedAtMs = System.currentTimeMillis()
                                        )
                                    }
                                    onRewardEarned()
                                }
                            }.onFailure {
                                onAdFailedToLoad?.invoke()
                            }
                        } else {
                            onAdFailedToLoad?.invoke()
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Rewarded ad failed for $effectiveId: ${loadAdError.message}")
                        if (effectiveId != TEST_REWARDED_AD_UNIT_ID) {
                            recordLiveFailureAndFallbackToTest("Mukofotli reklama", loadAdError)
                            showRewardedAd(activity, TEST_REWARDED_AD_UNIT_ID, onRewardEarned, onAdFailedToLoad)
                        } else {
                            _diagnostics.update {
                                it.copy(
                                    rewardedState = AdSlotLoadState.FAILED,
                                    lastUpdatedAtMs = System.currentTimeMillis()
                                )
                            }
                            Toast.makeText(
                                activity,
                                "Test reklama ham yuklanmadi (${loadAdError.code}). Internetni tekshiring.",
                                Toast.LENGTH_SHORT
                            ).show()
                            onAdFailedToLoad?.invoke()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "RewardedAd.load failed", t)
            _diagnostics.update {
                it.copy(
                    rewardedState = AdSlotLoadState.FAILED,
                    lastUpdatedAtMs = System.currentTimeMillis()
                )
            }
            onAdFailedToLoad?.invoke()
        }
    }

    /**
     * Loads and shows a full-screen Interstitial Ad.
     * Respects Test Mode; automatically falls back to Google's Test Interstitial if live load fails.
     */
    fun showInterstitialAd(
        activity: Activity,
        adUnitId: String? = null,
        onAdDismissed: () -> Unit = {},
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        val currentTestMode = _diagnostics.value.isTestMode
        val effectiveId = adUnitId ?: if (currentTestMode) TEST_INTERSTITIAL_AD_UNIT_ID else INTERSTITIAL_AD_UNIT_ID

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            activity,
            effectiveId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    val isTest = effectiveId == TEST_INTERSTITIAL_AD_UNIT_ID
                    Log.d(TAG, "Interstitial ad loaded successfully (isTest=$isTest).")
                    if (!isTest) recordLiveSuccess("Interstitial reklama")

                    interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad dismissed by user.")
                            onAdDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                            onAdDismissed()
                        }
                    }
                    interstitialAd.show(activity)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial ad failed to load for $effectiveId: ${loadAdError.message}")
                    if (effectiveId != TEST_INTERSTITIAL_AD_UNIT_ID) {
                        recordLiveFailureAndFallbackToTest("Interstitial reklama", loadAdError)
                        showInterstitialAd(activity, TEST_INTERSTITIAL_AD_UNIT_ID, onAdDismissed, onAdFailedToLoad)
                    } else {
                        onAdFailedToLoad?.invoke() ?: onAdDismissed()
                    }
                }
            }
        )
    }

    /**
     * Called when a ticket/quiz finishes.
     * Shows Rewarded Ad on every 5th completed ticket (5, 10, 15...),
     * and Interstitial Ad on all other tickets (1, 2, 3, 4, 6, 7, 8, 9...).
     */
    fun showCompletionAd(
        activity: Activity,
        completedCount: Int,
        onAdFinished: () -> Unit = {}
    ) {
        val isFifthTicket = completedCount > 0 && (completedCount % 5 == 0)
        if (isFifthTicket) {
            Log.d(TAG, "Showing Rewarded Ad for 5th ticket milestone (counter=$completedCount)")
            showRewardedAd(
                activity = activity,
                onRewardEarned = onAdFinished,
                onAdFailedToLoad = onAdFinished
            )
        } else {
            Log.d(TAG, "Showing Interstitial Ad for ticket completion (counter=$completedCount)")
            showInterstitialAd(
                activity = activity,
                onAdDismissed = onAdFinished,
                onAdFailedToLoad = onAdFinished
            )
        }
    }
}

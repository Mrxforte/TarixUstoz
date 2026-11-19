package com.tarixustoz.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.initialization.AdapterStatus
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

    // Real AdMob Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-2360068229850342/6027022065"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-2360068229850342/4905512084"

    // Test Ad Unit IDs provided by Google for verified Test Mode preview
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_ADAPTIVE_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

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

    private val _diagnostics = MutableStateFlow(AdMobDiagnosticsState())
    val diagnostics: StateFlow<AdMobDiagnosticsState> = _diagnostics.asStateFlow()

    @Volatile
    private var appPrefsContext: Context? = null

    fun initAdMob(context: Context) {
        val appContext = context.applicationContext
        appPrefsContext = appContext
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
                    // On first clean start without a previous mode lock, run a silent live probe
                    if (!prefs.contains(KEY_USE_TEST_MODE)) {
                        initScope.launch(Dispatchers.Main) {
                            probeLiveAdUnits(appContext)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AdMob", e)
                _diagnostics.update {
                    it.copy(
                        sdkInitialized = false,
                        adapterStatusSummary = "SDK xatosi: ${e.localizedMessage ?: "noma'lum"}",
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
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set RequestConfiguration", e)
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
        applyRequestConfiguration(false)
        _diagnostics.update {
            it.copy(
                liveProbeStatus = AdProbeStatus.TESTING_LIVE,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
        }
        try {
            val probeView = AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        recordLiveSuccess("Jonli Banner")
                        destroy()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        recordLiveFailureAndFallbackToTest("Jonli Banner", loadAdError)
                        destroy()
                    }
                }
            }
            probeView.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            setTestModeEnabled(
                enabled = true,
                reason = "Jonli tekshiruv xatosi: ${e.localizedMessage ?: "noma'lum"}"
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
        var localUnitId by remember(resolvedUnitId) { mutableStateOf(resolvedUnitId) }
        var adViewRef by remember { mutableStateOf<AdView?>(null) }

        LaunchedEffect(resolvedUnitId) {
            if (localUnitId != resolvedUnitId) {
                isAdLoaded = false
                localUnitId = resolvedUnitId
            }
        }

        LaunchedEffect(localUnitId) {
            while (isActive) {
                delay(autoRefreshIntervalSeconds * 1000L)
                adViewRef?.loadAd(AdRequest.Builder().build())
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            key(localUnitId) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        _diagnostics.update { it.copy(quizBannerState = AdSlotLoadState.LOADING) }
                        AdView(context).apply {
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
                                        _diagnostics.update {
                                            it.copy(
                                                quizBannerState = AdSlotLoadState.FAILED,
                                                lastUpdatedAtMs = System.currentTimeMillis()
                                            )
                                        }
                                    }
                                }
                            }
                            loadAd(AdRequest.Builder().build())
                            adViewRef = this
                        }
                    },
                    update = { adView ->
                        adViewRef = adView
                    }
                )
            }

            DisposableEffect(localUnitId) {
                onDispose {
                    adViewRef?.destroy()
                    adViewRef = null
                }
            }

            if (!isAdLoaded) {
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
                                    text = if (localUnitId == TEST_BANNER_AD_UNIT_ID) "TEST AD" else "Ad",
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
        var localUnitId by remember(resolvedUnitId) { mutableStateOf(resolvedUnitId) }
        var adViewRef by remember { mutableStateOf<AdView?>(null) }

        LaunchedEffect(resolvedUnitId) {
            if (localUnitId != resolvedUnitId) {
                isAdLoaded = false
                localUnitId = resolvedUnitId
            }
        }

        LaunchedEffect(localUnitId) {
            while (isActive) {
                delay(autoRefreshIntervalSeconds * 1000L)
                adViewRef?.loadAd(AdRequest.Builder().build())
            }
        }

        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val widthDpInt = maxWidth.value.toInt().coerceAtLeast(300)
            val heightDpInt = maxHeight.value.toInt().coerceIn(200, 260)

            key(localUnitId, widthDpInt, heightDpInt) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        _diagnostics.update { it.copy(heroBannerState = AdSlotLoadState.LOADING) }
                        val adSize = AdSize.getInlineAdaptiveBannerAdSize(widthDpInt, heightDpInt)
                        AdView(context).apply {
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
                            loadAd(AdRequest.Builder().build())
                            adViewRef = this
                        }
                    },
                    update = { adView ->
                        adViewRef = adView
                    }
                )
            }

            DisposableEffect(localUnitId, widthDpInt, heightDpInt) {
                onDispose {
                    adViewRef?.destroy()
                    adViewRef = null
                }
            }

            if (!isAdLoaded) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hero_admob_loading_placeholder")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
        var localUnitId by remember(resolvedUnitId) { mutableStateOf(resolvedUnitId) }
        var adViewRef by remember { mutableStateOf<AdView?>(null) }

        LaunchedEffect(resolvedUnitId) {
            if (localUnitId != resolvedUnitId) {
                isAdLoaded = false
                localUnitId = resolvedUnitId
            }
        }

        LaunchedEffect(localUnitId) {
            while (isActive) {
                delay(autoRefreshIntervalSeconds * 1000L)
                adViewRef?.loadAd(AdRequest.Builder().build())
            }
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .testTag("quiz_adaptive_banner_box"),
            contentAlignment = Alignment.Center
        ) {
            val widthDpInt = maxWidth.value.toInt().coerceAtLeast(300)
            val totalContainerHeightDp = maxHeight.value.toInt().coerceAtLeast(50)
            val heightBucketDp = computeInlineAdaptiveHeightBucket(totalContainerHeightDp)

            key(localUnitId, widthDpInt, heightBucketDp) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        _diagnostics.update { it.copy(quizBannerState = AdSlotLoadState.LOADING) }
                        val adaptiveSize = if (heightBucketDp >= 90) {
                            AdSize.getInlineAdaptiveBannerAdSize(widthDpInt, heightBucketDp)
                        } else {
                            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDpInt)
                        }
                        AdView(context).apply {
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
                            loadAd(AdRequest.Builder().build())
                            adViewRef = this
                        }
                    },
                    update = { adView ->
                        adViewRef = adView
                    }
                )
            }

            DisposableEffect(localUnitId, widthDpInt, heightBucketDp) {
                onDispose {
                    adViewRef?.destroy()
                    adViewRef = null
                }
            }

            if (!isAdLoaded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("quiz_responsive_ad_placeholder")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
    }
}

package com.tarixustoz.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object AdMobManager {

    private const val TAG = "AdMobManager"

    // Real AdMob Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-2360068229850342/6027022065"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-2360068229850342/4905512084"

    // Test Ad Unit IDs provided by Google for fallback preview
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    fun initAdMob(context: Context) {
        try {
            MobileAds.initialize(context) { initializationStatus ->
                Log.d(TAG, "AdMob Initialized: ${initializationStatus.adapterStatusMap}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob", e)
        }
    }

    /**
     * Auto-refreshing Banner Ad view matching exact banner dimensions.
     * Periodically updates ads every 30 seconds automatically.
     */
    @Composable
    fun BannerAdView(
        modifier: Modifier = Modifier,
        adUnitId: String = BANNER_AD_UNIT_ID,
        autoRefreshIntervalSeconds: Long = 30L
    ) {
        var isAdLoaded by remember { mutableStateOf(false) }
        var activeUnitId by remember { mutableStateOf(adUnitId) }
        var adViewRef by remember { mutableStateOf<AdView?>(null) }

        // Periodic auto-refresh every 30 seconds
        LaunchedEffect(activeUnitId) {
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
            key(activeUnitId) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        AdView(context).apply {
                            setAdSize(AdSize.BANNER)
                            setAdUnitId(activeUnitId)
                            adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    isAdLoaded = true
                                }

                                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                    Log.w(TAG, "Banner load failed for $activeUnitId: ${loadAdError.message}. Switching to test banner.")
                                    if (activeUnitId != TEST_BANNER_AD_UNIT_ID) {
                                        activeUnitId = TEST_BANNER_AD_UNIT_ID
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

            DisposableEffect(activeUnitId) {
                onDispose {
                    adViewRef?.destroy()
                    adViewRef = null
                }
            }

            // Fallback preview card matching banner size while loading
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
                                    text = "Ad",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "📢 Reklama maydoni (AdMob Banner)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "AdMob",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    /**
     * 100% Dedicated Large/Medium AdMob Banner Ad View for Hero Banner Slot.
     * Auto-refreshes every 30 seconds.
     */
    @Composable
    fun DedicatedHeroBannerAdView(
        modifier: Modifier = Modifier,
        adUnitId: String = BANNER_AD_UNIT_ID,
        autoRefreshIntervalSeconds: Long = 30L
    ) {
        var isAdLoaded by remember { mutableStateOf(false) }
        var activeUnitId by remember { mutableStateOf(adUnitId) }
        var adViewRef by remember { mutableStateOf<AdView?>(null) }

        LaunchedEffect(activeUnitId) {
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
            key(activeUnitId) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        AdView(context).apply {
                            setAdSize(AdSize.LARGE_BANNER)
                            setAdUnitId(activeUnitId)
                            adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    isAdLoaded = true
                                }

                                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                    Log.w(TAG, "Hero banner load failed for $activeUnitId: ${loadAdError.message}. Switching to test banner.")
                                    if (activeUnitId != TEST_BANNER_AD_UNIT_ID) {
                                        activeUnitId = TEST_BANNER_AD_UNIT_ID
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

            DisposableEffect(activeUnitId) {
                onDispose {
                    adViewRef?.destroy()
                    adViewRef = null
                }
            }

            if (!isAdLoaded) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B)
                            ) {
                                Text(
                                    text = "Google AdMob",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "📢 100% AdMob Reklama Maydoni",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads and shows a Rewarded Video Ad.
     * Falls back to test rewarded ad if primary ID is pending approval.
     */
    fun showRewardedAd(
        activity: Activity,
        adUnitId: String = REWARDED_AD_UNIT_ID,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                    rewardedAd.show(activity) { rewardItem ->
                        Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                        onRewardEarned()
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed for $adUnitId: ${loadAdError.message}. Trying test ad...")
                    if (adUnitId != TEST_REWARDED_AD_UNIT_ID) {
                        showRewardedAd(activity, TEST_REWARDED_AD_UNIT_ID, onRewardEarned, onAdFailedToLoad)
                    } else {
                        Toast.makeText(activity, "Reklama yuklanmadi. Internetingizni tekshirib, qayta urinib ko'ring.", Toast.LENGTH_SHORT).show()
                        onAdFailedToLoad?.invoke()
                    }
                }
            }
        )
    }
}

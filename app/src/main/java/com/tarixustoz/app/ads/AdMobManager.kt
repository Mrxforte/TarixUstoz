package com.tarixustoz.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdMobManager {

    private const val TAG = "AdMobManager"

    // Real AdMob Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-2360068229850342/6027022065"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-2360068229850342/4905512084"

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
     * Small Banner Ad view placed at the bottom of Quiz/Test screen.
     */
    @Composable
    fun BannerAdView(
        modifier: Modifier = Modifier,
        adUnitId: String = BANNER_AD_UNIT_ID
    ) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId(adUnitId)
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }

    /**
     * Loads and shows a Rewarded Video Ad.
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
                    Log.e(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    Toast.makeText(activity, "Reklama yuklanmadi. Qayta urinib ko'ring.", Toast.LENGTH_SHORT).show()
                    onAdFailedToLoad?.invoke()
                }
            }
        )
    }
}

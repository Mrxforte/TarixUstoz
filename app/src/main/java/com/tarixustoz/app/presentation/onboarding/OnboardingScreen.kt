package com.tarixustoz.app.presentation.onboarding

import android.app.Activity
import android.media.MediaPlayer
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tarixustoz.app.R
import com.tarixustoz.app.ads.findActivity
import kotlinx.coroutines.launch

private data class WelcomeSlide(
    @DrawableRes val imageRes: Int,
    val badge: String,
    val quote: String
)

private val WELCOME_SLIDES = listOf(
    WelcomeSlide(
        imageRes = R.drawable.intro_buxoro,
        badge = "Buxoro • O'zbekiston",
        quote = "“Tarix — o‘tmishning ko‘zgusi, buyuk kelajakning mustahkam poydevoridir.”"
    ),
    WelcomeSlide(
        imageRes = R.drawable.intro_registan,
        badge = "Registon • Samarqand",
        quote = "“Har bir sana, shaxs va voqea ortida ajdodlarimizning mangulikka muhrlangan hikmati bor.”"
    ),
    WelcomeSlide(
        imageRes = R.drawable.intro_xiva,
        badge = "Xiva • Qadimiy Xorazm",
        quote = "“O‘z tarixini chuqur anglagan inson kelajak yo‘lini dadil va ravshan ko‘ra oladi.”"
    )
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { WELCOME_SLIDES.size })

    var isMuted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {}
    }

    // Play looping Uzbek instrumental ambient music during welcome onboarding
    val mediaPlayer = remember {
        runCatching {
            MediaPlayer.create(context, R.raw.uzbek_history_ambient)?.apply {
                isLooping = true
                setVolume(0.45f, 0.45f)
                start()
            }
        }.getOrNull()
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            runCatching {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.stop()
                }
                mediaPlayer?.release()
            }
        }
    }

    val panTransition = rememberInfiniteTransition(label = "monumentPan")
    val panProgress by panTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "horizontalPan"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0B33))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val slide = WELCOME_SLIDES[page]
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp))
            ) {
                val maxPanPx = with(LocalDensity.current) { (maxWidth * 0.14f).toPx() }
                AsyncImage(
                    model = remember(slide.imageRes, context) {
                        ImageRequest.Builder(context)
                            .data(slide.imageRes)
                            .crossfade(false)
                            .build()
                    },
                    contentDescription = slide.badge,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.24f
                            scaleY = 1.24f
                            translationX = panProgress * maxPanPx
                        }
                )

                // Dark scrim for crisp readability across bright skies & monuments
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x990B0924),
                                    Color(0x330B0924),
                                    Color(0xB30B0924),
                                    Color(0xF20B0924)
                                )
                            )
                        )
                )
            }
        }

        // Top controls: Monument location pill + Audio mute toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeSlide = WELCOME_SLIDES[pagerState.currentPage]
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = activeSlide.badge,
                    color = Color(0xFFFDE68A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        runCatching {
                            val vol = if (isMuted) 0f else 0.45f
                            mediaPlayer?.setVolume(vol, vol)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("onboarding_audio_toggle")
                ) {
                    Icon(
                        imageVector = if (isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (isMuted) "Musiqani yoqish" else "Musiqani o'chirish",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom minimal overlay: one quote + dots indicator + "Boshlash" / "Keyingi" button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val activeSlide = WELCOME_SLIDES[pagerState.currentPage]

            Text(
                text = activeSlide.quote,
                color = Color.White,
                fontSize = 19.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(WELCOME_SLIDES.size) { index ->
                    val selected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 26.dp else 8.dp,
                        animationSpec = tween(250),
                        label = "dotWidth"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.45f),
                        animationSpec = tween(250),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            val isLastPage = pagerState.currentPage == WELCOME_SLIDES.lastIndex

            Button(
                onClick = {
                    if (!isLastPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        runCatching { mediaPlayer?.pause() }
                        viewModel.completeOnboarding {
                            onFinish()
                        }
                    }
                },
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF161244)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("onboarding_start_button")
            ) {
                Text(
                    text = if (isLastPage) "Boshlash" else "Keyingi",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF161244)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF161244),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

package com.example.d_r_i_v_e.frontend

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val AUTO_ADVANCE_INTERVAL_MS = 4_000L

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
    onLoginClick: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingItems.size })
    val scope = rememberCoroutineScope()

    // Carousel behavior: advance on its own, pausing while the user drags
    // and stopping on the last page so the Get Started CTA stays visible.
    LaunchedEffect(pagerState) {
        while (true) {
            snapshotFlow { pagerState.isScrollInProgress }.first { scrolling -> !scrolling }
            delay(AUTO_ADVANCE_INTERVAL_MS)

            if (pagerState.isScrollInProgress) continue

            val nextPage = pagerState.currentPage + 1
            if (nextPage > onboardingItems.lastIndex) continue

            pagerState.animateScrollToPage(
                page = nextPage,
                animationSpec = tween(
                    durationMillis = 650,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        HorizontalPager(
            state = pagerState,
            // Swiping stays available at any time; the carousel also advances itself.
            userScrollEnabled = true,
            beyondViewportPageCount = 1,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
            pageSpacing = 10.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPage(
                item = onboardingItems[page],
                isLastPage = onboardingItems[page].isLastPage,
                currentPage = pagerState.currentPage,
                totalPages = onboardingItems.size,
                pagerState = pagerState,
                page = page,
                onFinishClick = onFinished,
                onLoginClick = onLoginClick
            )
        }

        // Back button
        AnimatedVisibility(
            visible = pagerState.currentPage > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage - 1,
                            animationSpec = tween(durationMillis = 500)
                        )
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Skip button
        Text(
            text = "Skip",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onFinished() }
                .padding(8.dp)
        )
    }
}

@Composable
fun OnboardingPage(
    item: OnboardingItem,
    isLastPage: Boolean,
    currentPage: Int,
    totalPages: Int,
    pagerState: PagerState,
    page: Int,
    onFinishClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    // Signed page offset: 0 when settled on this page, -1 when this page is
    // one full screen to the right, +1 when it is one screen to the left.
    val pageOffset by remember(pagerState) {
        derivedStateOf {
            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        }
    }
    val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Neighbouring cards sit slightly back and dimmed, like a deck.
                val cardScale = 1f - absOffset * 0.08f
                scaleX = cardScale
                scaleY = cardScale
                alpha = 1f - absOffset * 0.28f
            }
            .clip(RoundedCornerShape(28.dp))
            .background(item.backgroundColor)
    ) {
        Image(
            painter = painterResource(id = item.imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Artwork trails the finger, so the text appears to slide
                    // over it at a faster, layered speed.
                    translationX = -pageOffset * size.width * 0.10f
                    val scale = 1.24f - absOffset * 0.04f
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - absOffset * 0.15f
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp)
                .graphicsLayer {
                    alpha = 1f - absOffset * 0.35f
                    translationX = pageOffset * size.width * 0.18f
                    translationY = absOffset * 28.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    translationX = pageOffset * 32.dp.toPx()
                    alpha = 1f - absOffset * 0.5f
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.graphicsLayer {
                    translationX = pageOffset * 16.dp.toPx()
                    alpha = 1f - absOffset * 0.4f
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            DotsIndicator(
                totalDots = totalPages,
                selectedIndex = currentPage
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLastPage) {
                // The last page has nowhere to swipe to, so it keeps the CTA
                // that completes onboarding.
                Button(
                    onClick = onFinishClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0B94))
                ) {
                    Text(
                        text = "Get Started",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "I already have an account",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onLoginClick() }
                        .padding(6.dp)
                )
            } else {
                Text(
                    text = "Swipe to continue",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun DotsIndicator(
    totalDots: Int,
    selectedIndex: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalDots) { index ->
            val isSelected = index == selectedIndex
            val dotWidth by animateDpAsState(
                targetValue = if (isSelected) 20.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "dotWidth"
            )
            val dotColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )
        }
    }
}
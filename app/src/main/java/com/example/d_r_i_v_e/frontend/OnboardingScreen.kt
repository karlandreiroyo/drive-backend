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
import androidx.compose.ui.draw.clipToBounds
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
        // Slides fill the whole screen; the footer floats on top of them.
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = true,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingSlide(
                item = onboardingItems[page],
                pagerState = pagerState,
                page = page
            )
        }

        OnboardingFooter(
            totalPages = onboardingItems.size,
            currentPage = pagerState.currentPage,
            isLastPage = pagerState.currentPage == onboardingItems.lastIndex,
            onFinishClick = onFinished,
            onLoginClick = onLoginClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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
private fun OnboardingSlide(
    item: OnboardingItem,
    pagerState: PagerState,
    page: Int
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
            .clipToBounds()
            .background(item.backgroundColor)
    ) {
        Image(
            painter = painterResource(id = item.imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The photo drifts at a slightly different speed than the
                    // page itself, keeping the slide feeling layered.
                    translationX = -pageOffset * size.width * 0.10f
                    val scale = 1.2f - absOffset * 0.03f
                    scaleX = scale
                    scaleY = scale
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 172.dp, start = 32.dp, end = 32.dp)
                .graphicsLayer {
                    alpha = 1f - absOffset * 0.35f
                    translationX = pageOffset * 36.dp.toPx()
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
                    translationX = pageOffset * 20.dp.toPx()
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
                    translationX = pageOffset * 10.dp.toPx()
                    alpha = 1f - absOffset * 0.4f
                }
            )
        }
    }
}

@Composable
private fun OnboardingFooter(
    totalPages: Int,
    currentPage: Int,
    isLastPage: Boolean,
    onFinishClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DotsIndicator(
            totalDots = totalPages,
            selectedIndex = currentPage
        )

        Spacer(modifier = Modifier.height(20.dp))

        // The 52dp slot is reserved on every page so slides never resize.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLastPage) {
                Button(
                    onClick = onFinishClick,
                    modifier = Modifier.fillMaxSize(),
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
            } else {
                Text(
                    text = "Swipe to browse",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Reserve the login-link height on every page for a stable layout.
        Box(
            modifier = Modifier.height(30.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLastPage) {
                Text(
                    text = "I already have an account",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onLoginClick() }
                        .padding(6.dp)
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
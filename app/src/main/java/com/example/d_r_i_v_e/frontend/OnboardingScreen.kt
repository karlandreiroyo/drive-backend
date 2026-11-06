package com.example.d_r_i_v_e.frontend

import androidx.compose.animation.AnimatedVisibility
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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
    onLoginClick: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingItems.size })
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPage(
                item = onboardingItems[page],
                isLastPage = onboardingItems[page].isLastPage,
                currentPage = pagerState.currentPage,
                totalPages = onboardingItems.size,
                pagerState = pagerState,
                page = page,
                onNextClick = {
                    if (onboardingItems[page].isLastPage) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = page + 1,
                                animationSpec = tween(durationMillis = 500)
                            )
                        }
                    }
                },
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
    onNextClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val pageOffset = remember(pagerState.currentPage, pagerState.currentPageOffsetFraction, page) {
        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(item.backgroundColor)
    ) {
        Image(
            painter = painterResource(id = item.imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - (pageOffset * 0.3f).coerceIn(0f, 0.3f)
                    scaleX = 1f + (pageOffset * 0.05f)
                    scaleY = 1f + (pageOffset * 0.05f)
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
                    alpha = 1f - pageOffset.coerceIn(0f, 1f)
                    translationY = pageOffset * 40f
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            DotsIndicator(
                totalDots = totalPages,
                selectedIndex = currentPage
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNextClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F0B94))
            ) {
                Text(
                    text = if (isLastPage) "Get Started" else "Next",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isLastPage) {
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
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(if (isSelected) 20.dp else 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.4f)
                    )
            )
        }
    }
}
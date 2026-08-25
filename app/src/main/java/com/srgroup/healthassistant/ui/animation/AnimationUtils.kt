package com.srgroup.healthassistant.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin

// ─── Screen Transition (tab change) ───────────────────────────────────────────

val smoothFadeSlideIn: EnterTransition =
    fadeIn(tween(300, easing = EaseOut)) +
    slideInHorizontally(
        animationSpec = tween(300, easing = EaseOut),
        initialOffsetX = { it / 6 }   // subtle — only 1/6 of screen width
    )

val smoothFadeSlideOut: ExitTransition =
    fadeOut(tween(200, easing = EaseIn)) +
    slideOutHorizontally(
        animationSpec = tween(200, easing = EaseIn),
        targetOffsetX = { -it / 6 }
    )

// ─── Button press scale effect ─────────────────────────────────────────────

@Composable
fun AnimatedPressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    Box(modifier = modifier.scale(scale)) {
        content()
    }
}

// ─── Fade-in on first compose (for list items, cards) ─────────────────────

@Composable
fun FadeInContent(
    delayMs: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, delayMillis = 0, easing = EaseOut)) +
                slideInVertically(
                    animationSpec = tween(400, easing = EaseOut),
                    initialOffsetY = { it / 5 }
                ),
        modifier = modifier
    ) {
        content()
    }
}

// ─── Pulsing loading indicator (for AI reply) ──────────────────────────────

@Composable
fun PulsingDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(modifier = modifier.scale(scale))
}

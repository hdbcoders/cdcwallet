package com.hdbcoders.cdcwallet

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/** Navigation motion duration - short enough to feel snappy, long enough to read. */
private const val NAV_TRANSITION_MS = 280

/**
 * Drill-in motion for push/pop navigation (list → detail / add): the incoming
 * screen slides in from the right, the outgoing one slides out to the left.
 * Reverse for pop. All specs collapse to [EnterTransition.None] /
 * [ExitTransition.None] when the user has system reduce-motion enabled.
 */
internal fun drillInEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInHorizontally(tween(NAV_TRANSITION_MS)) { it } + fadeIn(tween(NAV_TRANSITION_MS))

internal fun drillInExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutHorizontally(tween(NAV_TRANSITION_MS)) { -it / 3 } + fadeOut(tween(NAV_TRANSITION_MS))

internal fun drillInPopEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInHorizontally(tween(NAV_TRANSITION_MS)) { -it / 3 } + fadeIn(tween(NAV_TRANSITION_MS))

internal fun drillInPopExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutHorizontally(tween(NAV_TRANSITION_MS)) { it } + fadeOut(tween(NAV_TRANSITION_MS))

/**
 * Layer motion for modal-ish screens (archived / settings): the incoming
 * screen slides up from the bottom like a sheet; pop slides it back down.
 * Collapses to no motion under system reduce-motion.
 */
internal fun layerEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInVertically(tween(NAV_TRANSITION_MS)) { it } + fadeIn(tween(NAV_TRANSITION_MS))

internal fun layerExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutVertically(tween(NAV_TRANSITION_MS)) { it / 3 } + fadeOut(tween(NAV_TRANSITION_MS))

internal fun layerPopEnter(reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) EnterTransition.None
    else slideInVertically(tween(NAV_TRANSITION_MS)) { it / 3 } + fadeIn(tween(NAV_TRANSITION_MS))

internal fun layerPopExit(reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) ExitTransition.None
    else slideOutVertically(tween(NAV_TRANSITION_MS)) { it } + fadeOut(tween(NAV_TRANSITION_MS))

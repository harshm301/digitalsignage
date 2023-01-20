package com.example.digitalsignage

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.transition.TransitionValues
import android.transition.Visibility
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import kotlin.math.hypot

class CircularRevealTransition : Visibility() {
    override fun onAppear(
        sceneRoot: ViewGroup?,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val startRadius = 0
        val endRadius = hypot(view.width.toDouble(), view.height.toDouble()).toInt()
        val reveal: Animator = ViewAnimationUtils.createCircularReveal(
            view,
            view.getWidth() / 2,
            view.getHeight() / 2,
            startRadius.toFloat(),
            endRadius.toFloat()
        )
        //make view invisible until animation actually starts
        view.setAlpha(0F)
        reveal.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                view.setAlpha(1F)
            }
        })
        return reveal
    }

    override fun onDisappear(
        sceneRoot: ViewGroup?,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val endRadius = 0
        val startRadius =
            Math.hypot(view.getWidth().toDouble(), view.getHeight().toDouble()).toInt()
        return ViewAnimationUtils.createCircularReveal(
            view,
            view.getWidth() / 2,
            view.getHeight() / 2,
            startRadius.toFloat(),
            endRadius.toFloat()
        )
    }
}
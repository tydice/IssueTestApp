package com.our.shot.presentation.common.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.OnTouchListener
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * Helper to detect taps using Android GestureDetector, and pass the taps between UI thread and
 * render thread.
 */
class TapHelper(
    context: Context?,
    onUpdateScale: (Float) -> Unit,
    onUpdateCoords: (Float, Float) -> Unit
) : OnTouchListener {
    private val gestureDetector: GestureDetector
    private val scaleDetector: ScaleGestureDetector
    private val queuedSingleTaps: BlockingQueue<MotionEvent> = ArrayBlockingQueue(16)

    private var scaleOngoing = false

    /**
     * Creates the tap helper.
     *
     * @param context the application's context.
     *
     */
    init {
        gestureDetector = GestureDetector(
            context,
            object : SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // Queue tap if there is space. Tap is lost if queue is full.
                    queuedSingleTaps.offer(e)
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }
            }
        )

        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                    scaleOngoing = true
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector?) {
                    scaleOngoing = false
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    onUpdateScale(detector.scaleFactor)

                    //scaleFactor *= detector.scaleFactor
                    //// Don't let the object get too small or too large.
                    //scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f))

                    return true
                }
            }
        )
    }

    /**
     * Polls for a tap.
     *
     * @return if a tap was queued, a MotionEvent for the tap. Otherwise null if no taps are queued.
     */
    fun poll(): MotionEvent? {
        return queuedSingleTaps.poll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(motionEvent)

        return true
    }
}

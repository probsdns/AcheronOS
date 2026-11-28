package com.example.acheronos

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.*
import android.widget.*
import android.widget.TextView
import kotlin.math.*

class IrisService : VoiceInteractionService()

class IrisSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return IrisSession(this)
    }
}

class IrisSession(context: Context) : VoiceInteractionSession(context) {

    private var muted = false

    private fun dp(value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    override fun onCreateContentView(): View {

        window?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setDecorFitsSystemWindows(false)

            attributes = attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        }

        val root = FrameLayout(context)

        val popup = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(191, 98, 98, 98))
                cornerRadius = dp(20f).toFloat()
            }
            clipToOutline = true
        }

        val popupParams = FrameLayout.LayoutParams(
            -1, -1
        ).apply {
            setMargins(dp(12f), dp(12f), dp(12f), dp(12f))
        }

        root.addView(popup, popupParams)

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout()
            )

            popupParams.setMargins(
                bars.left + dp(12f),
                maxOf(
                    bars.top,
                    if (minOf(
                        context.resources.configuration.screenWidthDp,
                        context.resources.configuration.screenHeightDp
                    ) >= 600) dp(48f) else 0
                ) + dp(12f),
                bars.right + dp(12f),
                bars.bottom + dp(12f)
            )

            popup.layoutParams = popupParams
            insets
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16f), 0, 0)
        }

        popup.addView(
            header,
            FrameLayout.LayoutParams(
                -1, -2, Gravity.TOP
            )
        )

        val title = TextView(context).apply {
            text = "Iris"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        header.addView(title)

        val muteRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        header.addView(
            muteRow,
            LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = dp(8f)
            }
        )

        val muteLabel = TextView(context).apply {
            text = "Mute Iris"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        muteRow.addView(muteLabel)

        val waveform = IrisWaveform(context)

        val toggle = IrisMuteToggle(context) { state ->
            muted = state
            waveform.muted = state
        }

        muteRow.addView(
            toggle,
            LinearLayout.LayoutParams(
                dp(60f), dp(32f)
            ).apply {
                marginStart = dp(8f)
            }
        )

        popup.addView(
            waveform,
            FrameLayout.LayoutParams(
                -1, dp(64f), Gravity.BOTTOM
            ).apply {
                setMargins(
                    dp(12f), 0,
                    dp(12f), dp(20f)
                )
            }
        )

        root.requestApplyInsets()

        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        android.util.Log.i("IrisDebug", "Iris session opened")
        window?.window?.setLayout(-1, -1)
    }
}

class IrisMuteToggle(
    context: Context,
    private val onToggle: (Boolean) -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var muted = false
    private var position = 0f
    private var animator: ValueAnimator? = null

    init {
        contentDescription = "Mute Iris"
        isClickable = true

        setOnClickListener {
            muted = !muted
            animator?.cancel()

            animator = ValueAnimator.ofFloat(
                position,
                if (muted) 1f else 0f
            ).apply {
                duration = 250
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }

            onToggle(muted)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = h / 16f

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(57, 57, 57)

        canvas.drawRoundRect(
            0f, 0f, w, h,
            h / 2, h / 2, paint
        )

        paint.color = Color.rgb(144, 144, 144)

        canvas.drawCircle(
            h / 2 + (w - h) * position,
            h / 2,
            h / 2 - padding,
            paint
        )

        drawSpeaker(canvas, h / 2, h / 2, false)
        drawSpeaker(canvas, w - h / 2, h / 2, true)
    }

    private fun drawSpeaker(
        canvas: Canvas,
        x: Float,
        y: Float,
        crossed: Boolean
    ) {
        canvas.save()
        canvas.translate(x, y)

        val scale = height / 32f
        canvas.scale(scale, scale)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        val speaker = Path().apply {
            moveTo(-8f, -3f)
            lineTo(-5f, -3f)
            lineTo(0f, -7f)
            lineTo(0f, 7f)
            lineTo(-5f, 3f)
            lineTo(-8f, 3f)
            close()
        }

        canvas.drawPath(speaker, paint)

        if (crossed) {
            canvas.drawLine(4f, -3f, 9f, 3f, paint)
            canvas.drawLine(9f, -3f, 4f, 3f, paint)
        } else {
            canvas.drawArc(
                -2f, -5f, 7f, 5f,
                -55f, 110f, false, paint
            )
            canvas.drawArc(
                -4f, -9f, 11f, 9f,
                -50f, 100f, false, paint
            )
        }

        canvas.restore()
    }
}

class IrisWaveform(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 238, 238)
    }

    var muted = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val barWidth = 3f * density
        val gap = 3f * density

        val count = max(
            1,
            ((width + gap) / (barWidth + gap)).toInt()
        )

        val time = android.os.SystemClock.uptimeMillis() / 1000.0
        val centre = height / 2f

        val step = if (count > 1)
            (width - barWidth) / (count - 1)
        else 0f

        for (i in 0 until count) {

            val envelope = sin(
                PI * i / max(1, count - 1)
            )

            val motion = abs(
                sin(time * (5.0 + i % 7) + i * 1.7)
            )

            val barHeight = if (muted) {
                3f * density
            } else {
                (3f + 49f * envelope * motion).toFloat() * density
            }

            val x = i * step

            canvas.drawRoundRect(
                x,
                centre - barHeight / 2,
                x + barWidth,
                centre + barHeight / 2,
                barWidth / 2,
                barWidth / 2,
                paint
            )
        }

        if (!muted && isShown) {
            postInvalidateOnAnimation()
        }
    }
}

class IrisRecognitionService : RecognitionService() {

    override fun onStartListening(
        recognizerIntent: Intent,
        listener: Callback
    ) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) {}

    override fun onCancel(listener: Callback) {}
}

class IrisActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(TextView(this).apply {
            text = "Iris"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        })
    }
}

package com.vip.visionassist

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpoken = ""
    private var lastSpokenTime = 0L
    private val cooldownMs = 2500L

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                isReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.95f)
                Log.i("VIP", "TTS ready: $isReady")
            } else {
                Log.e("VIP", "TTS init failed")
            }
        }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!isReady) return
        val now = System.currentTimeMillis()
        if (!force && text == lastSpoken && now - lastSpokenTime < cooldownMs) return
        lastSpoken = text
        lastSpokenTime = now
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vip_tts")
        Log.d("VIP", "Speaking: $text")
    }

    fun vibrate(durationMs: Long = 300) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.e("VIP", "Vibrate error: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
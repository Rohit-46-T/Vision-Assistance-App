package com.vip.visionassist

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceAssistant(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private var lastSpokenTime = 0L
    private val cooldown = 3000L // 3 seconds cooldown between warnings

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VIP", "TTS Language not supported")
            } else {
                isReady = true
                Log.d("VIP", "TTS Initialized")
            }
        } else {
            Log.e("VIP", "TTS Initialization failed")
        }
    }

    fun warn(label: String) {
        val currentTime = System.currentTimeMillis()
        if (isReady && currentTime - lastSpokenTime > cooldown) {
            val message = "$label ahead"
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenTime = currentTime
            Log.d("VIP", "Spoke: $message")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

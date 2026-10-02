package com.resqlink.emergency.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Continuously listens for "call help" / "sos" / "emergency".
 * Microphone must be granted; listening starts every time the app is open.
 */
class VoiceCommandService(
    private val context: Context,
    private val onHelpCalled: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var shouldListen = false

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        shouldListen = true
        restart()
    }

    fun stopListening() {
        shouldListen = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun restart() {
        if (!shouldListen) return
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (shouldListen) restart()
                }
                override fun onError(error: Int) {
                    if (shouldListen) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (shouldListen) restart()
                        }, 800)
                    }
                }
                override fun onResults(results: Bundle?) {
                    checkPhrases(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    if (shouldListen) restart()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    checkPhrases(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
        }
    }

    private fun checkPhrases(matches: ArrayList<String>?) {
        matches?.forEach { phrase ->
            val lower = phrase.lowercase(Locale.ROOT)
            if (lower.contains("call help") ||
                lower.contains("sos") ||
                lower.contains("emergency") ||
                lower.contains("help me")
            ) {
                onHelpCalled()
            }
        }
    }
}

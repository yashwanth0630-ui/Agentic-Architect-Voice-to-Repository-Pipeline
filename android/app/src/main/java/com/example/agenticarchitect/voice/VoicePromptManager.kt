package com.example.agenticarchitect.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Manages Android native SpeechRecognizer for on-device voice prompt ingestion
 * with robust offline handling and fallback support.
 */
class VoicePromptManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit
    ) {
        mainHandler.post {
            stopListening()

            if (!isAvailable()) {
                onError("Speech recognition not available. Tap mic or use sample prompts below.")
                onListeningStateChange(false)
                return@post
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onListeningStateChange(true)
                        }

                        override fun onBeginningOfSpeech() {
                            onListeningStateChange(true)
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            onListeningStateChange(false)
                        }

                        override fun onError(error: Int) {
                            onListeningStateChange(false)
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                                SpeechRecognizer.ERROR_CLIENT -> "Client side error. Restarting listener..."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                                SpeechRecognizer.ERROR_NETWORK -> "Network error. Using on-device speech engine."
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak closer to microphone."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy. Resetting..."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
                                else -> "Speech recognition code: $error"
                            }
                            onError(errorMsg)
                        }

                        override fun onResults(results: Bundle?) {
                            onListeningStateChange(false)
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                                onResult(matches[0])
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!partialMatches.isNullOrEmpty() && partialMatches[0].isNotBlank()) {
                                onResult(partialMatches[0])
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                }

                speechRecognizer?.startListening(intent)
                onListeningStateChange(true)
            } catch (e: Exception) {
                onListeningStateChange(false)
                onError("Failed to start mic listener: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            } finally {
                speechRecognizer = null
            }
        }
    }
}


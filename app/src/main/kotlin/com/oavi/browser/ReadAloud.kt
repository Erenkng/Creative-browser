package com.oavi.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.oavi.browser.model.SpeechText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Reads a page aloud with the phone's own text-to-speech voice, a piece at a time, so it can
 * be paused and continued. Nothing leaves the phone.
 */
class ReadAloud(context: Context) {
    enum class Status { IDLE, PREPARING, SPEAKING, PAUSED }

    data class State(val status: Status = Status.IDLE, val title: String = "", val progress: Float = 0f)

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Called when the phone has no usable voice. */
    var onUnavailable: (() -> Unit)? = null

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var pieces: List<String> = emptyList()
    private var index = 0
    private var language: Locale? = null

    /** Shows that the page's text is being fetched. */
    fun preparing() {
        stopSpeech()
        _state.value = State(Status.PREPARING)
    }

    /** Starts reading [text]. Returns false when there is nothing to read. */
    fun start(title: String, text: String, languageTag: String?): Boolean {
        pieces = SpeechText.chunks(text)
        if (pieces.isEmpty()) {
            stop()
            return false
        }
        index = 0
        language = languageTag?.let { Locale.forLanguageTag(it) }?.takeIf { it.language.isNotEmpty() }
        _state.value = State(Status.SPEAKING, title, 0f)
        withEngine { speakCurrent() }
        return true
    }

    fun pause() {
        if (_state.value.status != Status.SPEAKING) return
        _state.update { it.copy(status = Status.PAUSED) }
        engine?.stop()
    }

    fun resume() {
        if (_state.value.status != Status.PAUSED) return
        _state.update { it.copy(status = Status.SPEAKING) }
        withEngine { speakCurrent() }
    }

    fun stop() {
        stopSpeech()
        pieces = emptyList()
        _state.value = State()
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun stopSpeech() {
        try {
            engine?.stop()
        } catch (_: RuntimeException) {
        }
    }

    private fun withEngine(then: () -> Unit) {
        if (engine != null && ready) {
            then()
            return
        }
        pending = then
        if (engine != null) return
        engine = TextToSpeech(app) { status ->
            main.post {
                ready = status == TextToSpeech.SUCCESS
                if (!ready) {
                    engine?.shutdown()
                    engine = null
                    pending = null
                    _state.value = State()
                    onUnavailable?.invoke()
                    return@post
                }
                engine?.setOnUtteranceProgressListener(progress)
                pending?.invoke()
                pending = null
            }
        }
    }

    private fun speakCurrent() {
        val voice = engine ?: return
        if (_state.value.status != Status.SPEAKING) return
        if (index >= pieces.size) {
            stop()
            return
        }
        val wanted = language
        if (wanted != null && voice.isLanguageAvailable(wanted) >= TextToSpeech.LANG_AVAILABLE) {
            voice.language = wanted
        }
        voice.speak(pieces[index], TextToSpeech.QUEUE_FLUSH, null, "$UTTERANCE$index")
        _state.update { it.copy(progress = index.toFloat() / pieces.size) }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            main.post {
                if (_state.value.status == Status.SPEAKING && utteranceId == "$UTTERANCE$index") {
                    index++
                    speakCurrent()
                }
            }
        }

        @Deprecated("Deprecated in the platform; kept for older engines")
        override fun onError(utteranceId: String?) {
            main.post { stop() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { stop() }
        }
    }

    private companion object {
        const val UTTERANCE = "oavi-"
    }
}

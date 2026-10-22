package ru.ark.radio

import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class ArkPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEqualizer(audioSessionId)
            })
        }
        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()
        if (exoPlayer!!.audioSessionId != C.AUDIO_SESSION_ID_UNSET) attachEqualizer(exoPlayer!!.audioSessionId)
    }

    private fun attachEqualizer(id: Int) {
        try {
            effect?.release()
            effect = Equalizer(0, id).apply { enabled = true }
            audioSessionId = id
        } catch (_: Exception) {
            effect = null
            audioSessionId = 0
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        effect?.release()
        effect = null
        audioSessionId = 0
        mediaSession?.run { player.release(); release() }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        @Volatile var audioSessionId: Int = 0
            private set
        @Volatile private var effect: Equalizer? = null

        fun bandCount(): Int = try { effect?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }
        fun bandRange(): Pair<Int, Int> = try {
            val r = effect?.bandLevelRange
            if (r != null && r.size >= 2) Pair(r[0].toInt(), r[1].toInt()) else Pair(-1500, 1500)
        } catch (_: Exception) { Pair(-1500, 1500) }
        fun centerFrequencyHz(band: Int): Int = try { (effect?.getCenterFreq(band.toShort()) ?: 0) / 1000 } catch (_: Exception) { 0 }
        fun level(band: Int): Int = try { effect?.getBandLevel(band.toShort())?.toInt() ?: 0 } catch (_: Exception) { 0 }
        fun setLevel(band: Int, level: Int) { try { effect?.setBandLevel(band.toShort(), level.coerceIn(-1500, 1500).toShort()) } catch (_: Exception) {} }
        fun resetBands() { for (i in 0 until bandCount()) setLevel(i, 0) }
        fun applyPreset(preset: Int) {
            try {
                val e = effect ?: return
                if (preset in 0 until e.numberOfPresets.toInt()) e.usePreset(preset.toShort())
            } catch (_: Exception) {}
        }
        fun presetNames(): List<String> {
            return try {
                val e = effect ?: return emptyList()
                (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            } catch (_: Exception) { emptyList() }
        }
    }
}

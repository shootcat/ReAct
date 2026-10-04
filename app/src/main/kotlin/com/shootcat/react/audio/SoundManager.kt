package com.shootcat.react.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import com.shootcat.react.R
import kotlin.random.Random

/**
 * The game's sound: short effects for what happens in the world (fire hisses out, water splashes,
 * steam gathers into a cloud) and a quiet, meditative melody for each world.
 *
 * Effects are named like the engine's cues ("hiss", "splash", "rain", …). Every call is safe to make at
 * any time – before loading has finished, with sound switched off, or where audio is not available at all
 * (unit tests): then it simply stays silent.
 */
class SoundManager(context: Context) {

    private val app = context.applicationContext
    private var pool: SoundPool? = null
    private val loaded = HashMap<String, Int>()
    private val ready = HashSet<Int>()
    private val lastPlayed = HashMap<String, Long>()
    private var player: MediaPlayer? = null
    private var playerTrack: Int? = null

    private var effectsOn = true
    private var musicOn = true
    private var foreground = false
    private var track = 1

    init {
        runCatching {
            val p = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
            for ((name, res) in EFFECTS) loaded[name] = p.load(app, res, 1)
            pool = p
        }
    }

    fun configure(effects: Boolean, music: Boolean) {
        effectsOn = effects
        musicOn = music
        updateMusic()
    }

    /** Plays the effect [name]; the same effect at most every few frames, so cascades do not roar. */
    fun play(name: String, volume: Float = 1f) {
        if (!effectsOn) return
        val p = pool ?: return
        val id = loaded[name] ?: return
        if (id !in ready) return
        val now = SystemClock.uptimeMillis()
        if (now - (lastPlayed[name] ?: 0L) < MIN_GAP_MILLIS) return
        lastPlayed[name] = now
        val v = (volume * (VOLUMES[name] ?: 0.8f)).coerceIn(0f, 1f)
        // A little variation keeps repeated sounds natural.
        val rate = 0.92f + Random.nextFloat() * 0.16f
        runCatching { p.play(id, v, v, 1, 0, rate) }
    }

    /** The melody of world [world]. */
    fun setTrack(world: Int) {
        if (world == track) return
        track = world
        updateMusic()
    }

    fun setForeground(on: Boolean) {
        foreground = on
        updateMusic()
    }

    private fun updateMusic() {
        val wanted = musicOn && foreground
        if (!wanted) {
            runCatching { player?.takeIf { it.isPlaying }?.pause() }
            return
        }
        if (playerTrack != track) {
            runCatching { player?.release() }
            player = null
            playerTrack = null
            val res = MUSIC[track] ?: MUSIC.getValue(1)
            player = runCatching {
                MediaPlayer.create(app, res)?.apply {
                    isLooping = true
                    setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                }
            }.getOrNull()
            playerTrack = track
        }
        runCatching { player?.takeIf { !it.isPlaying }?.start() }
    }

    fun release() {
        runCatching { pool?.release() }
        runCatching { player?.release() }
        pool = null
        player = null
        playerTrack = null
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val MIN_GAP_MILLIS = 90L
        const val MUSIC_VOLUME = 0.32f

        val EFFECTS = mapOf(
            "hiss" to R.raw.sfx_hiss,
            "boil" to R.raw.sfx_boil,
            "crackle" to R.raw.sfx_crackle,
            "freeze" to R.raw.sfx_freeze,
            "melt" to R.raw.sfx_melt,
            "sizzle" to R.raw.sfx_sizzle,
            "soak" to R.raw.sfx_soak,
            "splash" to R.raw.sfx_splash,
            "sprout" to R.raw.sfx_sprout,
            "thud" to R.raw.sfx_thud,
            "whoosh" to R.raw.sfx_whoosh,
            "condense" to R.raw.sfx_condense,
            "rain" to R.raw.sfx_rain,
            "bounce" to R.raw.sfx_bounce,
            "place" to R.raw.sfx_place,
            "merge" to R.raw.sfx_merge,
            "goal" to R.raw.sfx_goal,
            "success" to R.raw.sfx_success,
        )

        val VOLUMES = mapOf(
            "hiss" to 0.7f,
            "boil" to 0.5f,
            "crackle" to 0.55f,
            "rain" to 0.6f,
            "place" to 0.6f,
            "bounce" to 0.7f,
            "success" to 0.75f,
        )

        val MUSIC = mapOf(
            1 to R.raw.music_forest,
            2 to R.raw.music_coast,
            3 to R.raw.music_volcano,
            4 to R.raw.music_frost,
        )
    }
}

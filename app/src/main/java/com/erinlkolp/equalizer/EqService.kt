package com.erinlkolp.equalizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.SharedPreferences
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class EqService : Service() {

    private val eqSessions = mutableMapOf<Int, DynamicsProcessing>()
    private lateinit var sharedPrefs: SharedPreferences
    
    // Fallback global effect
    private var globalEq: DynamicsProcessing? = null

    // Standard 31-band 1/3 octave frequencies in Hz
    private val frequencies = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f, 800f, 
        1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
    )

    companion object {
        private var instance: EqService? = null
        
        fun handleSessionEvent(action: String?, sessionId: Int) {
            val service = instance ?: return
            if (action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                service.applyEqToSession(sessionId)
            } else if (action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION) {
                service.removeEqFromSession(sessionId)
            }
        }

        fun updateBand(bandIndex: Int, gainDb: Float) {
            val service = instance ?: return
            val eqBand = DynamicsProcessing.EqBand(true, service.frequencies[bandIndex], gainDb)
            val dps = service.eqSessions.values.toMutableList()
            if (service.globalEq != null) dps.add(service.globalEq!!)
            for (dp in dps) {
                dp.setPreEqBandAllChannelsTo(bandIndex, eqBand)
            }
        }

        fun updatePreamp(gainDb: Float) {
            val service = instance ?: return
            val dps = service.eqSessions.values.toMutableList()
            if (service.globalEq != null) dps.add(service.globalEq!!)
            for (dp in dps) {
                try {
                    dp.setInputGainAllChannelsTo(gainDb)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sharedPrefs = getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            "ACTION_START" -> {
                startForeground(1, createNotification())
                applyGlobalEq() // Attempt Session 0
                updateAllBands()
            }
            "ACTION_STOP" -> {
                releaseAll()
                stopForeground(true)
                stopSelf()
            }
            "ACTION_UPDATE_BANDS" -> {
                updateAllBands()
            }
            "ACTION_UPDATE_PREAMP" -> {
                val preampProgress = sharedPrefs.getInt("preamp_gain", 120)
                updatePreamp(EqHelper.progressToGainDb(preampProgress))
            }
            "ACTION_OPEN_SESSION" -> {
                val sessionId = intent.getIntExtra("session_id", -1)
                if (sessionId != -1) {
                    applyEqToSession(sessionId)
                }
            }
            "ACTION_CLOSE_SESSION" -> {
                val sessionId = intent.getIntExtra("session_id", -1)
                if (sessionId != -1) {
                    removeEqFromSession(sessionId)
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "eq_channel",
                "Equalizer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "eq_channel")
            .setContentTitle("31-Band Equalizer is Active")
            .setContentText("Tap to configure")
            .setSmallIcon(android.R.drawable.ic_media_play) // Placeholder icon
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createDynamicsProcessing(sessionId: Int): DynamicsProcessing? {
        // Try creating with anti-clipping limiter first, fall back without limiter if unsupported
        return try {
            buildDynamicsProcessing(sessionId, withLimiter = true)
        } catch (e: Exception) {
            android.util.Log.w("EqService", "Creating with limiter failed, falling back to without limiter", e)
            try {
                buildDynamicsProcessing(sessionId, withLimiter = false)
            } catch (e2: Exception) {
                android.util.Log.e("EqService", "Failed to create DynamicsProcessing", e2)
                null
            }
        }
    }

    private fun buildDynamicsProcessing(sessionId: Int, withLimiter: Boolean): DynamicsProcessing {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // stereo
            true, // preEqInUse
            31, // preEqBandCount
            false, // mbcInUse
            0, // mbcBandCount
            false, // postEqInUse
            0, // postEqBandCount
            withLimiter // limiterInUse
        )
        
        val eq = DynamicsProcessing.Eq(
            true, // inUse
            true, // enabled
            31 // activeBandCount
        )
        
        // Set up all 31 bands
        for (i in 0 until 31) {
            val progress = sharedPrefs.getInt("band_$i", 120)
            val gainDb = (progress - 120) / 10.0f
            val eqBand = DynamicsProcessing.EqBand(
                true, // enabled
                frequencies[i], // cutoff frequency
                gainDb // gain
            )
            eq.setBand(i, eqBand)
        }
        
        val preampProgress = sharedPrefs.getInt("preamp_gain", 120)
        val preampGainDb = EqHelper.progressToGainDb(preampProgress)

        val channel = DynamicsProcessing.Channel(
            preampGainDb, // inputGain
            true, // preEqInUse
            31, // preEqBandCount
            false, // mbcInUse
            0, // mbcBandCount
            false, // postEqInUse
            0, // postEqBandCount
            withLimiter // limiterInUse
        )
        channel.preEq = eq

        if (withLimiter) {
            // Anti-clipping studio-grade peak limiter (1ms attack, 50ms release, 10:1 ratio, -0.1 dB threshold)
            val limiter = DynamicsProcessing.Limiter(
                true,  // inUse
                true,  // enabled
                0,     // linkGroup (0 = stereo linked to preserve stereo imaging)
                1.0f,  // attackTime ms
                50.0f, // releaseTime ms
                10.0f, // ratio
                -0.1f, // threshold dB
                0.0f   // postGain dB
            )
            channel.limiter = limiter
        }
        
        val config = builder
            .setPreferredFrameDuration(10f)
            .setChannelTo(0, channel)
            .setChannelTo(1, channel)
            .build()

        val dp = DynamicsProcessing(0, sessionId, config)
        dp.enabled = true
        return dp
    }

    private fun applyGlobalEq() {
        if (globalEq == null) {
            globalEq = createDynamicsProcessing(0) // Session 0
        }
    }

    private fun applyEqToSession(sessionId: Int) {
        android.util.Log.d("EqService", "Attempting to apply EQ to session: $sessionId")
        if (!sharedPrefs.getBoolean("eq_enabled", false)) return
        if (eqSessions.containsKey(sessionId)) return
        
        val dp = createDynamicsProcessing(sessionId)
        if (dp != null) {
            android.util.Log.d("EqService", "Successfully applied EQ to session: $sessionId")
            eqSessions[sessionId] = dp
        } else {
            android.util.Log.e("EqService", "Failed to create DynamicsProcessing for session: $sessionId")
        }
    }

    private fun removeEqFromSession(sessionId: Int) {
        eqSessions[sessionId]?.let {
            it.enabled = false
            it.release()
        }
        eqSessions.remove(sessionId)
    }

    private fun updateAllBands() {
        val gains = FloatArray(31)
        for (i in 0 until 31) {
            val progress = sharedPrefs.getInt("band_$i", 120)
            gains[i] = (progress - 120) / 10.0f
        }
        val preampProgress = sharedPrefs.getInt("preamp_gain", 120)
        val preampGainDb = EqHelper.progressToGainDb(preampProgress)

        val dps = eqSessions.values.toMutableList()
        if (globalEq != null) dps.add(globalEq!!)

        for (dp in dps) {
            try {
                dp.setInputGainAllChannelsTo(preampGainDb)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            for (i in 0 until 31) {
                // To dynamically update a band across all channels
                val eqBand = DynamicsProcessing.EqBand(
                    true, // enabled
                    frequencies[i], // cutoff frequency
                    gains[i] // gain
                )
                dp.setPreEqBandAllChannelsTo(i, eqBand)
            }
        }
    }

    private fun releaseAll() {
        for (dp in eqSessions.values) {
            dp.enabled = false
            dp.release()
        }
        eqSessions.clear()
        
        globalEq?.enabled = false
        globalEq?.release()
        globalEq = null
    }

    override fun onDestroy() {
        instance = null
        releaseAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

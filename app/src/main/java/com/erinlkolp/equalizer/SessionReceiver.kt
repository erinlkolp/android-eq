package com.erinlkolp.equalizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        if (sessionId == AudioEffect.ERROR_BAD_VALUE) return

        EqService.handleSessionEvent(intent.action, sessionId)
    }
}

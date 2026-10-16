package io.github.seijikohara.femto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

private const val TAG = "CarVoiceTriggerReceiver"

internal class CarVoiceTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        Log.i(TAG, "Car voice broadcast received: action=$action")

        if (action == Intent.ACTION_MEDIA_BUTTON) {
            @Suppress("DEPRECATION")
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                Log.i(TAG, "Media button received: keyCode=${keyEvent.keyCode}")
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_HEADSETHOOK,
                    KeyEvent.KEYCODE_VOICE_ASSIST,
                    KeyEvent.KEYCODE_ASSIST,
                    KeyEvent.KEYCODE_SEARCH,
                    KeyEvent.KEYCODE_MEDIA_RECORD,
                    KeyEvent.KEYCODE_CALL,
                    KeyEvent.KEYCODE_MUTE,
                    -> {
                        launchAssistant(context)
                    }
                }
            }
            return
        }

        launchAssistant(context)
    }

    private fun launchAssistant(context: Context) {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VOICE_COMMAND
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        context.startActivity(launchIntent)
    }
}

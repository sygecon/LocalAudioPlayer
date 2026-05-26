package com.aspada.localaudioplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSessionService;

import java.util.Objects;

public class AudioPlaybackService extends MediaSessionService
{
    private static MediaSession mediaSession = null;

    @Override
    public void onCreate() {
        super.onCreate();

        AudioAttributes mPlaybackAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)    // AudioAttributes.CONTENT_TYPE_MUSIC | AudioAttributes.CONTENT_TYPE_SPEECH
            .build();

        ExoPlayer player = new ExoPlayer.Builder(this)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.fromPlatformAudioAttributes(mPlaybackAttributes), true
            ).build();

        player.setShuffleModeEnabled(false);
        // player.setWakeMode(PowerManager.PARTIAL_WAKE_LOCK);
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setHandleAudioBecomingNoisy(true);

        mediaSession = new MediaSession.Builder(this, player).build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                // обновляем уведомление, панель и т.д.
                if (playWhenReady) {
                    // началось воспроизведение
                    registerPhoneCallReceiver();
                } else {
                    // пауза
                    unRegisterPhoneCallReceiver();
                }
            }
        });
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    /**
     * Broadcast Receiver registered to receive the MEDIA_BUTTON intent coming from clients
     */
    private BroadcastReceiver PhoneCallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

                if (state != null) {
                    if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        // Телефон звонит - поставить на паузу
                        if (mediaSession.getPlayer().isPlaying()) {
                            mediaSession.getPlayer().setPlayWhenReady(false);
                        }
                    } else
                    if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                        // Звонок принят - поставить на паузу
                        if (mediaSession.getPlayer().isPlaying()) {
                            mediaSession.getPlayer().setPlayWhenReady(false);
                        }
                    } else
                    if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        // Звонок завершен - можно возобновить
                        if (! mediaSession.getPlayer().isPlaying() && mediaSession.getPlayer().getCurrentMediaItem() != null) {
                            mediaSession.getPlayer().setPlayWhenReady(true);
                        }
                    }
                }
            }
        }
    };

    private void registerPhoneCallReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(PhoneCallReceiver, filter);
    }

    private void unRegisterPhoneCallReceiver() {
        try {
            if (PhoneCallReceiver != null) {
                unregisterReceiver(PhoneCallReceiver);
                PhoneCallReceiver = null;
            }
        } catch (IllegalArgumentException e) {
            Log.e("MediaReceiver", "Receiver not registered", e);
        }
    }

    @Override
    public void onDestroy() {
        unRegisterPhoneCallReceiver();

        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}

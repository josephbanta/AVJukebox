package com.josephbanta.avjukebox.service;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;

import com.josephbanta.avjukebox.R;
import com.josephbanta.avjukebox.TrackListActivity;
//import android.os.IBinder;

public class MediaPlayerService extends MessagableService
{
    // constants
    public final String LOG_TAG = MediaPlayerService.class.getSimpleName();

    public enum NotificationIds {
        PLAYING_STREAM
    }

    public enum StreamState {
        QUEUED,
        FADING_IN,
        PLAYING,
        FADING_OUT,
        FADING_OUT_PRIOR_TO_PAUSE,
        PAUSED,
        ERROR,
        FINISHED
    }

    public enum MessagesToService {
        REGISTER_CLIENT,
        DEREGISTER_CLIENT,
        PREFERENCE_UPDATE,
        QUERY_STATUS,
        PLAY_STREAM,
        FADE_IN,
        FADE_OUT,
        SCHEDULED_FADE_OUT,
        PAUSE_STREAM,
        STOP_STREAM,
    }

    public enum MessagesToClient {
        UNRECOGNIZED_REQUEST,
        PLAYER_STATUS,
        READY_FOR_PLAYBACK,
        ERROR_PLAYING_STREAM,
        STREAM_INFO_AVAIALABLE,
        BUFFERING_UPDATE,
        PLAYING_STREAM_COMPLETE,
        FADE_IN_COMPLETED,
        FADE_OUT_COMPLETED,
        STREAM_STATE_CHANGED
    }

    // member variables
    private android.app.NotificationManager mNotificationManager; // For showing and hiding our notification.

    // Keeps track of all current registered clients.
    private java.util.ArrayList<android.os.Messenger> mClients = new java.util.ArrayList<android.os.Messenger>();
    private class PlayerInfo {
        public android.media.MediaPlayer MediaPlayer;
        public StreamState   State;
        public float[]       Volume;
        public String        Title;
        public String        StreamUrl;
        public String        ClipContext;
    }

    private java.util.HashMap<String, PlayerInfo> mediaPlayers;

    private MediaPlayerListener mediaPlayerListener = new MediaPlayerListener();
    private IncomingCallInterceptor incomingCallReceiver = new IncomingCallInterceptor();

    private java.util.ArrayList<String> queuedIds = null;
    private java.util.ArrayList<String> queuedUrls = null;
    private java.util.ArrayList<String> queuedTitles = null;
    private int nextClipQueued = 0;

    private java.util.HashMap<String, Object> preferences = new java.util.HashMap<String, Object>();


    //////////////////////////////////////////////////////////////////////
    // constructors
    //////////////////////////////////////////////////////////////////////

    public MediaPlayerService() {
    }

    //////////////////////////////////////////////////////////////////////
    // methods overridden from class
    //                       android.app.Service
    //////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        //android.media.AudioManager audioManager = (android.media.AudioManager) this.getSystemService(android.content.Context.AUDIO_SERVICE);
        //int maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        //audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVolume, 0);

        mediaPlayers = new java.util.HashMap<String, PlayerInfo>();

        registerReceiver(incomingCallReceiver, new android.content.IntentFilter("android.intent.action.PHONE_STATE"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //String title = null;

        //android.os.Bundle extras = intent.getExtras();
        //if (intent.getExtras() != null) {
        //    path = extras.getString("path");
        //    title = extras.getString("title");
        //}

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        for (PlayerInfo streamState : mediaPlayers.values()) {
            if (streamState.MediaPlayer != null) {
                try {
                    streamState.MediaPlayer.stop();
                    streamState.MediaPlayer.release();
                } catch (Exception exception) {
                    android.util.Log.d(LOG_TAG, "Error destroying media player: " + exception);
                } finally {
                    streamState.MediaPlayer = null;
                }
            }
        }

        unregisterReceiver(incomingCallReceiver);
    }

    @Override
    public void onLowMemory() {
        // TODO Auto-generated method stub
        super.onLowMemory();
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String message = "Sorry your system has low memory";
        android.app.Notification notification = new android.app.Notification(android.R.drawable.ic_dialog_alert, message, System.currentTimeMillis());
        notificationManager.notify(1, notification);
        stopSelf();
    }

    //////////////////////////////////////////////////////////////////////
    // Methods overridden from abstract class
    //                        MessagableService
    //////////////////////////////////////////////////////////////////////

    /**
     * The implementation of this method will be called when a bound client sends a message
     */
    @Override
    public boolean handleIncomingMessage(android.os.Message msg) {
        boolean messageHandled = false;

        if (msg.what == MessagesToService.REGISTER_CLIENT.ordinal()) {
            mClients.add(msg.replyTo);

            android.os.Bundle messageData = msg.getData();
            for (String key : messageData.keySet()) {
                preferences.put(key, messageData.get(key));
            }

            sendPlayerState(msg.replyTo);
        }
        else if (msg.what == MessagesToService.DEREGISTER_CLIENT.ordinal()) {
            mClients.remove(msg.replyTo);
        }
        else if (msg.what == MessagesToService.PREFERENCE_UPDATE.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            for (String key : messageData.keySet()) {
                preferences.put(key, messageData.get(key));
            }
        }
        else if (msg.what == MessagesToService.QUERY_STATUS.ordinal()) {
            sendPlayerState(msg.replyTo);
        }
        else if (msg.what == MessagesToService.PLAY_STREAM.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            String clipId = messageData.getString("id");

            // since a request has been submitted from an external client to play a stream
            // (as opposed to being submitted due to an auto-play action by this service),
            // make note of any streams that will need to be faded out when this clip becomes
            // fully buffered
            if ( (preferences.get("oneClipAtATime") == Boolean.TRUE)
              && (preferences.get("fadeInOut") == Boolean.TRUE) )
            {
                for (String otherClipId : mediaPlayers.keySet()) {
                    if ( !(otherClipId.equals(clipId)) ) {
                        PlayerInfo otherPlayerInfo = mediaPlayers.get(otherClipId);
                        if ( (otherPlayerInfo.State == StreamState.FADING_IN)
                          || (otherPlayerInfo.State == StreamState.PLAYING) )
                        {
                            // we will look for this later to know which streams to pause after a fade-out
                            otherPlayerInfo.State = StreamState.FADING_OUT_PRIOR_TO_PAUSE;
                        }
                    }
                }

            }

            String url = messageData.getString("url");
            String title = messageData.getString("title");
            String trackContext = messageData.getString("trackContext");
            this.queuedIds = messageData.getStringArrayList("queuedIds");
            this.queuedUrls = messageData.getStringArrayList("queuedUrls");
            this.queuedTitles = messageData.getStringArrayList("queuedTitles");
            float volume = messageData.getFloat("volume");

            playStream(clipId, url, title, trackContext, volume);
        }

        else if (msg.what == MessagesToService.PAUSE_STREAM.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            String clipId = messageData.getString("id");
            final PlayerInfo thisPlayer = mediaPlayers.get(clipId);
            if (thisPlayer.MediaPlayer != null) {
                thisPlayer.MediaPlayer.pause();
                updatePlayerStatus(clipId, StreamState.PAUSED);
            }
        }
        else if (msg.what == MessagesToService.STOP_STREAM.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            String clipId = messageData.getString("id");
            PlayerInfo thisStreamState = mediaPlayers.get(clipId);

            if (thisStreamState != null) {
                try {
                    thisStreamState.MediaPlayer.stop();
                    thisStreamState.MediaPlayer.release();
                } catch (Exception exception) {
                    android.util.Log.d(LOG_TAG, "Error destroying media player: " + exception);
                } finally {
                    thisStreamState.MediaPlayer = null;
                }
            }
        }
        else if (msg.what == MessagesToService.FADE_IN.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            String clipId = messageData.getString("id");
            final int fadeInPeriodMilliseconds = messageData.getInt("period");
            java.util.ArrayList<String> streamsToFadeOut = messageData.getStringArrayList("streamsToFadeOut");
            float[] targetLevel = messageData.getFloatArray("targetLevel");
            if (targetLevel == null) {
                targetLevel = new float[] {1.0f, 1.0f};
            }

            java.util.ArrayList<PlayerInfo> playersToFadeOut = new java.util.ArrayList<PlayerInfo>();
            java.util.ArrayList<float[]> startVolume = new java.util.ArrayList<float[]>();
            if (streamsToFadeOut != null) {
                for (String clipIdToFadeOut : streamsToFadeOut) {
                    PlayerInfo streamInfo = mediaPlayers.get(clipIdToFadeOut);
                    if (streamInfo != null) {
                        playersToFadeOut.add(streamInfo);
                        startVolume.add(streamInfo.Volume);
                    }
                }
            }

            android.util.Log.d(LOG_TAG, "handleIncomingMessage(FADE_IN)");
            final PlayerInfo thisStreamState = mediaPlayers.get(clipId);
            if (thisStreamState != null) {
                //AsyncTask.execute(
                //        new Runnable() {
                //            @Override
                //            public void run() {
                                thisStreamState.MediaPlayer.setVolume(0.0f, 0.0f);
                                if (thisStreamState.State == StreamState.PAUSED) {
                                    thisStreamState.MediaPlayer.start();
                                }
                                updatePlayerStatus(clipId, StreamState.FADING_IN);

                                int numSteps = 10;
                                int delayEachStep = Math.max(0, (fadeInPeriodMilliseconds/numSteps) - 30); // the constant is to allow some time for the work to be done during each step
                                for (int currentStep=0; currentStep<numSteps; currentStep++) {
                                    if ((currentStep != 0) && (delayEachStep > 0)) {try {java.lang.Thread.sleep(delayEachStep, 0);} catch (InterruptedException ie) {}}
                                    float logval = (float) (Math.log(numSteps - currentStep) / Math.log(numSteps)),
                                          volume = 1 - logval;
                                    android.util.Log.v(LOG_TAG, "handleIncomingMessage(FADE_IN) - setting volume to (" + (volume*targetLevel[0]) + "," + (volume*targetLevel[1]) + ")");
                                    thisStreamState.Volume = new float[] {volume*targetLevel[0], volume*targetLevel[1]};
                                    try {
                                        thisStreamState.MediaPlayer.setVolume(thisStreamState.Volume[0], thisStreamState.Volume[1]);
                                    } catch (java.lang.IllegalStateException illegalStateException) {
                                        android.util.Log.d(LOG_TAG, "Error setting volume on media player: " + illegalStateException);
                                    }

                                    for (int i=0, numFadeOutPlayers=playersToFadeOut.size(); i<numFadeOutPlayers; i++) {
                                        PlayerInfo fadeOutStream = playersToFadeOut.get(i);
                                        float[] initialVolume    = startVolume.get(i);

                                        fadeOutStream.Volume = new float[] {logval*initialVolume[0], logval*initialVolume[1]};
                                        try {
                                            fadeOutStream.MediaPlayer.setVolume(thisStreamState.Volume[0], thisStreamState.Volume[1]);
                                        } catch (java.lang.IllegalStateException illegalStateException) {
                                            android.util.Log.d(LOG_TAG, "Error setting volume on media player: " + illegalStateException);
                                        }
                                    }
                                }
                                updatePlayerStatus(clipId, StreamState.PLAYING);

                                if (streamsToFadeOut != null) {
                                    for (String fadedOutStream : streamsToFadeOut) {
                                        PlayerInfo fadedOutPlayer = mediaPlayers.get(fadedOutStream);
                                        if (fadedOutPlayer.State == StreamState.FADING_OUT_PRIOR_TO_PAUSE) {
                                            fadedOutPlayer.MediaPlayer.pause();
                                            updatePlayerStatus(fadedOutStream, StreamState.PAUSED);
                                        }
                                    }
                                }
                //            }
                //        } );

            }
        }
        else if ( (msg.what == MessagesToService.SCHEDULED_FADE_OUT.ordinal())
               || (msg.what == MessagesToService.FADE_OUT.ordinal()) )
        {
            android.os.Bundle messageData = msg.getData();
            String clipId = messageData.getString("id");
            final int delayMilliseconds = (msg.what == MessagesToService.SCHEDULED_FADE_OUT.ordinal()) ? messageData.getInt("after") : 0;
            final int fadeOutPeriodMilliseconds = messageData.getInt("period");

            final PlayerInfo thisStreamState = mediaPlayers.get(clipId);
            if (thisStreamState != null) {
                //AsyncTask.execute(new Runnable() {
                //        @Override
                //        public void run() {
                            float[] startVolume = thisStreamState.Volume;
                            if (startVolume == null) {
                                startVolume = new float[] {1.0f, 1.0f};
                            }
                            int numSteps = 10;
                            int delayEachStep = Math.max(0, (fadeOutPeriodMilliseconds/numSteps) - 30); // the constant is to allow some time for the work to be done during each step
                            int currentPlayerPosition = thisStreamState.MediaPlayer.getCurrentPosition();
                            for (int currentStep = 0; currentStep < numSteps; currentStep++) {
                                if (currentStep == 0) {
                                    if (delayMilliseconds != 0) {
                                        try {
                                            java.lang.Thread.sleep(delayMilliseconds, 0);
                                        } catch (InterruptedException ie) {}
                                    }
                                }
                                else if (delayEachStep > 0) {
                                    try {
                                        java.lang.Thread.sleep(delayEachStep, 0);
                                    } catch (InterruptedException ie) {}
                                }

                                float logVol = (float) (Math.log(numSteps - currentStep) / Math.log(numSteps));
                                try {
                                    android.util.Log.d(LOG_TAG, "handleIncomingMessage(FADE_OUT) - setting volume to " + logVol);
                                    thisStreamState.Volume = new float[] {logVol*startVolume[0], logVol*startVolume[1]};
                                    thisStreamState.MediaPlayer.setVolume(thisStreamState.Volume[0], thisStreamState.Volume[1]);
                                } catch (IllegalStateException ise) {
                                    // we got an error while lowering the volume, which probably means that the stream has stopped playing; disregard the error
                                    break;
                                }
                            }

                            broadcastMessageToPlayerClients(MessagesToClient.FADE_OUT_COMPLETED, clipId);

                //        }
                //    } );
            }
        }
        else {
            for (int i=mClients.size()-1; i>=0; i--) {
                try {
                    mClients.get(i).send(android.os.Message.obtain(null,
                            MessagesToClient.UNRECOGNIZED_REQUEST.ordinal(), msg.what, 0));
                } catch (android.os.RemoteException e) {
                    // The client is dead.  Remove it from the list;
                    // we are going through the list from back to front
                    // so this is safe to do inside the loop.
                    mClients.remove(i);
                }
            }
            messageHandled = false;
        }

        return messageHandled;
    }


    //////////////////////////////////////////////////////////////////////
    // methods unique to this class (non-overridden)
    //////////////////////////////////////////////////////////////////////

    private java.util.HashMap<String, java.util.HashMap> getPlayerStatus () {
        java.util.HashMap<String, java.util.HashMap>  streamState = new java.util.HashMap<String, java.util.HashMap>();
        for (String url : mediaPlayers.keySet()) {
            PlayerInfo thisStreamState = mediaPlayers.get(url);
            int duration = 0;
            int currentPosition = 0;

            boolean isPlaying = false, isQueued=false;
            try {
                currentPosition = thisStreamState.MediaPlayer.getCurrentPosition();
                isPlaying = thisStreamState.MediaPlayer.isPlaying();
                isQueued = false;
            } catch (java.lang.IllegalStateException illegalStateException) {
                isPlaying = false;
                isQueued = true;
            }

            if (isPlaying) {
                duration = thisStreamState.MediaPlayer.getDuration();
            }
            else {
                try {
                    duration = thisStreamState.MediaPlayer.getDuration();
                } catch (java.lang.IllegalStateException illegalStateException) {
                    // the player isn't in a state where the duration is available.  We'll assume that the player is still preparing...
                }
            }

            java.util.HashMap streamStateMap = new java.util.HashMap();
            streamStateMap.put("state",           thisStreamState.State);
            streamStateMap.put("currentPosition", currentPosition);
            streamStateMap.put("duration",        duration);
            streamStateMap.put("volume",          thisStreamState.Volume);
            streamStateMap.put("title",           thisStreamState.Title);
            streamStateMap.put("context",         thisStreamState.ClipContext);

            streamState.put(url, streamStateMap);
        }

        return streamState;
    }

    private void sendPlayerState (android.os.Messenger recipient) {
        java.util.HashMap<String, java.util.HashMap> streamState = getPlayerStatus();

        android.os.Message response = createMessageToMediaPlayerClients(MessagesToClient.PLAYER_STATUS, null);
        android.os.Bundle responseData = response.getData();
        responseData.putSerializable("streamState", streamState);
        try {
            recipient.send(response);
        } catch (android.os.RemoteException e) {
            android.util.Log.d(LOG_TAG, "Error responding to status query: " + e);
        }
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification (String text, String trackContext) {
        android.content.Intent trackListActivityIntent = new android.content.Intent(this, TrackListActivity.class);
        trackListActivityIntent.putExtra("queryText", trackContext);
        trackListActivityIntent.setAction(Long.toString(System.currentTimeMillis())); // so that intent is unique, and extras will be preserved

        // The PendingIntent to launch our activity if the user selects this notification
        android.app.PendingIntent contentIntent
                = android.app.PendingIntent.getActivity(
                    this,
                    0,
                    trackListActivityIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT ); // FLAG_CANCEL_CURRENT , FLAG_UPDATE_CURRENT

        android.app.Notification notification = new android.app.Notification.Builder(this)
                .setContentTitle(text)
                .setContentText(getText(R.string.app_name))
                .setContentIntent(contentIntent)
                .setSmallIcon(com.josephbanta.avjukebox.R.drawable.ic_launcher)
                //.setLargeIcon(aBitmap)
                .getNotification();

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNotificationManager.notify(NotificationIds.PLAYING_STREAM.ordinal(), notification);

        android.content.Intent intent = new android.content.Intent();
        intent.setAction("com.josephbanta.avjukebox");
        intent.putExtra("NowPlaying", text);
        sendBroadcast(intent);
    }

    private String getPlayerClipId (android.media.MediaPlayer mediaPlayer)
    {
        String thisPlayerClipId = null;
        for (String clipId : mediaPlayers.keySet()) {
            PlayerInfo thisStreamState = mediaPlayers.get(clipId);
            if (mediaPlayer.equals(thisStreamState.MediaPlayer)) {
                thisPlayerClipId = clipId;
                break;
            }
        }
        return thisPlayerClipId;
    }

    public void playStream (String clipId, String url, String title, String trackContext, float volume) {
        PlayerInfo thisStream = mediaPlayers.get(clipId);
        if (thisStream != null) {
            // we are being asked to play a stream that we already have a player for; assume that it is paused
            boolean fadeInAndOut = (preferences.get("fadeInOut") == Boolean.TRUE);
            java.util.ArrayList<String> streamsToFadeOut = new java.util.ArrayList<String>();
            if (preferences.get("oneClipAtATime") == Boolean.TRUE) {
                // immediately pause any currently playing audio, then pause it
                for (String otherClipId : mediaPlayers.keySet()) {
                    if ( !(otherClipId.equals(clipId)) ) {
                        PlayerInfo otherPlayerInfo = mediaPlayers.get(otherClipId);
                        if ( (otherPlayerInfo.State == StreamState.QUEUED)
                          || (otherPlayerInfo.State == StreamState.FADING_IN)
                          || (otherPlayerInfo.State == StreamState.PLAYING)
                          || (otherPlayerInfo.State == StreamState.FADING_OUT_PRIOR_TO_PAUSE) )
                        {
                            if (fadeInAndOut == false) {
                                otherPlayerInfo.MediaPlayer.pause();
                                updatePlayerStatus(otherClipId, StreamState.PAUSED);
                            }
                            else {
                                streamsToFadeOut.add(otherClipId);
                            }
                        }
                    }
                }
            }

            if (fadeInAndOut == true) {
                fadeInPlayer(clipId, streamsToFadeOut);
            }
            else {
                thisStream.MediaPlayer.setVolume(volume, volume);
                thisStream.Volume = new float[]{volume, volume};
                thisStream.MediaPlayer.start();

                updatePlayerStatus(clipId, StreamState.PLAYING);
            }
            // TODO: show a notification?
        }
        else {
            if (preferences.get("oneClipAtATime") == Boolean.TRUE) {
                // if any other streams are queued, pause them before they buffer up to a playing state
                for (String otherClipId : mediaPlayers.keySet()) {
                    if (!(otherClipId.equals(clipId))) {
                        PlayerInfo otherPlayerInfo = mediaPlayers.get(otherClipId);
                        if (otherPlayerInfo.State == StreamState.QUEUED) {
                            otherPlayerInfo.MediaPlayer.pause();
                            updatePlayerStatus(otherClipId, StreamState.PAUSED);
                        }
                    }
                }
            }


            PlayerInfo newStream = new PlayerInfo();
            newStream.MediaPlayer = new android.media.MediaPlayer();
            newStream.MediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            newStream.MediaPlayer.setOnErrorListener(mediaPlayerListener);
            newStream.MediaPlayer.setOnCompletionListener(mediaPlayerListener);
            newStream.MediaPlayer.setOnInfoListener(mediaPlayerListener);
            newStream.MediaPlayer.setOnBufferingUpdateListener(mediaPlayerListener);
            newStream.MediaPlayer.setOnPreparedListener(mediaPlayerListener);

            if (preferences.get("fadeInOut") == Boolean.TRUE) {
                // if the audio is to faded in, start it at a level of 0
                newStream.MediaPlayer.setVolume(0.0f, 0.0f);
            }
            else {
                newStream.MediaPlayer.setVolume(volume, volume);
            }
            newStream.Volume = new float[] {volume, volume};
            newStream.ClipContext = trackContext;
            newStream.Title = title;
            newStream.StreamUrl = url;

            //if (player != null) {
            //    player.setLooping(true);
            //    //mPlayer.setVolume(100,100);
            //}

            android.util.Log.i("", "PlayService will play " + url);
            try {
                if (newStream.MediaPlayer != null) {
                    newStream.MediaPlayer.reset();
                    newStream.MediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
                    newStream.MediaPlayer.setDataSource(newStream.StreamUrl);
                    if (title != null) {
                        showNotification(title, trackContext);
                    }
                    newStream.MediaPlayer.setLooping(false);
                    newStream.MediaPlayer.prepare();
                    android.util.Log.d(LOG_TAG, "PlayService player.prepare() returned");
                    newStream.MediaPlayer.start();
//                CustomNotification();
                    mediaPlayers.put(clipId, newStream);
                    updatePlayerStatus(clipId, StreamState.QUEUED);

                    android.util.Log.i(LOG_TAG, "player.start() returned");
                } else {
                    android.util.Log.i(LOG_TAG, "mediaplayer null");
                }
                //updateNotification(true);
            } catch (java.io.IOException e) {
                android.util.Log.e(LOG_TAG, "PlayService::onStart() IOException attempting player.prepare()\n");
                android.widget.Toast t = android.widget.Toast.makeText(getApplicationContext(), "PlayService was unable to start playing recording: " + e, android.widget.Toast.LENGTH_LONG);
                t.show();
                // return;
            } catch (java.lang.Exception e) {
                android.widget.Toast t = android.widget.Toast.makeText(getApplicationContext(), "MusicPlayer was unable to start playing recording: " + e, android.widget.Toast.LENGTH_LONG);
                t.show();

                android.util.Log.e(LOG_TAG, "PlayService::onStart caught unexpected exception", e);
            }
        } // if (thisStream == null)
    } // public void playStream (String path, String title, float volume)

/*
    public void pauseMusic(String path) {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            length = mPlayer.getCurrentPosition();

        } else {
            mPlayer.reset();
            mPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            try {
                mPlayer.setDataSource(path);
            } catch (IllegalArgumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (SecurityException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (java.io.IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mPlayer.setLooping(false);
            try {
                mPlayer.prepare();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (java.io.IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            android.util.Log.d("logtag", "PlayService player.prepare() returned");
            mPlayer.start();
        }
    }

    public void playNextSong(String path) {
        mPlayer.stop();
        mPlayer.reset();
        mPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
        try {
            mPlayer.setDataSource(path);
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (java.io.IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mPlayer.setLooping(false);
        try {
            mPlayer.prepare();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (java.io.IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        android.util.Log.d("logtag", "PlayService player.prepare() returned");
        mPlayer.start();
    }

    public boolean isplaying() {
        if (mPlayer != null) {
            return mPlayer.isPlaying();
        }
        return false;
    }

    public void seekto(int duration) {
        if (mPlayer != null) {
            mPlayer.seekTo(duration);
        }
    }

    public int getCurrentPosition() {
        if (mPlayer != null) {
            return mPlayer.getCurrentPosition();
        }
        return 0;

    }

    public int getDuration() {
        if (mPlayer != null) {
            return mPlayer.getDuration();
        }
        return 0;

    }

    public void resumeMusic() {
        if (mPlayer.isPlaying() == false) {
            mPlayer.seekTo(length);
            mPlayer.start();
        }
    }

    public void stopMusic() {
        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
    }


    public void release() {
        mPlayer.release();
    }
*/

    private android.os.Message createMessageToMediaPlayerClients ( MediaPlayerService.MessagesToClient messageType )
    {
        return createMessageToMediaPlayerClients(messageType, null);
    }

    private android.os.Message createMessageToMediaPlayerClients ( MediaPlayerService.MessagesToClient messageType,
                                                                   String clipId )
    {
        android.os.Message msg = android.os.Message.obtain(
                null,
                messageType.ordinal(), 0, 0);

        if (clipId != null) {
            android.os.Bundle messageData = msg.getData();
            messageData.putString("id", clipId);
        }

        return msg;
    }

    private void broadcastMessageToPlayerClients (MediaPlayerService.MessagesToClient messageType)
            //throws android.os.RemoteException,
            //java.io.NotActiveException
    {
        broadcastMessageToPlayerClients(createMessageToMediaPlayerClients(messageType));
    }

    private void broadcastMessageToPlayerClients (MediaPlayerService.MessagesToClient messageType, String messageString)
            //throws android.os.RemoteException,
            //java.io.NotActiveException
    {
        broadcastMessageToPlayerClients(createMessageToMediaPlayerClients(messageType, messageString));
    }

    private void broadcastMessageToPlayerClients (android.os.Message msg)
            //throws android.os.RemoteException,
            //       java.io.NotActiveException
    {
        if (mClients.size() == 0) {
            //throw new java.io.NotActiveException("Service not connected");
        }
        else {
            for (int i=mClients.size()-1; i>=0; i--) {
                try {
                    mClients.get(i).send(msg);
                } catch (android.os.RemoteException e) {
                    // The client is dead.  Remove it from the list;
                    // we are going through the list from back to front
                    // so this is safe to do inside the loop.
                    mClients.remove(i);
                }
            }
        }
    }

    private void updatePlayerStatus (String clipId, StreamState newState) {
        PlayerInfo playerInfo = mediaPlayers.get(clipId);
        if (playerInfo != null) {
            if (playerInfo.State != newState) {
                android.os.Message message = createMessageToMediaPlayerClients(MessagesToClient.STREAM_STATE_CHANGED, clipId);
                message.arg1 = newState.ordinal();         // arg1 will be new state
                if (playerInfo.State == null) {
                    message.arg2 = -1;
                }
                else {
                    message.arg2 = playerInfo.State.ordinal(); // arg2 will be old state
                }

                android.os.Bundle messageData = message.getData();
                messageData.putFloatArray("volume", playerInfo.Volume);
                messageData.putString("title",      playerInfo.Title);

                broadcastMessageToPlayerClients(message);

                playerInfo.State = newState;

                if (newState == StreamState.PLAYING) {
                    showNotification (playerInfo.Title, playerInfo.ClipContext);
                }
            }

            // look at all the service's streams and if none is playing, hide the service's notifications
            boolean anyPlayerPlaying = false;
            for (PlayerInfo otherPlayerInfo : mediaPlayers.values()) {
                if ( (otherPlayerInfo.State == StreamState.QUEUED)
                  || (otherPlayerInfo.State == StreamState.FADING_IN)
                  || (otherPlayerInfo.State == StreamState.PLAYING)
                  || (otherPlayerInfo.State == StreamState.FADING_OUT)
                  || (otherPlayerInfo.State == StreamState.FADING_OUT_PRIOR_TO_PAUSE) )
                {
                    anyPlayerPlaying = true;
                }
            }
            if (anyPlayerPlaying == false) {
                mNotificationManager.cancel(NotificationIds.PLAYING_STREAM.ordinal());
            }

        }
    }

    private void fadeOutPlayer(final String clipId, final boolean pauseAfterwards)
    {
        final PlayerInfo playerInfo = mediaPlayers.get(clipId);
        if ( (playerInfo != null)
          && ( (playerInfo.State == StreamState.FADING_IN)
            || (playerInfo.State == StreamState.PLAYING) ) )
        {
            AsyncTask.execute(new Runnable() {
                    public void run() {
                        updatePlayerStatus(clipId, pauseAfterwards ? StreamState.FADING_OUT_PRIOR_TO_PAUSE : StreamState.FADING_OUT);

                        android.os.Message fadeOutMessage = android.os.Message.obtain(
                                null,
                                MessagesToService.FADE_OUT.ordinal());
                        android.os.Bundle bundle = fadeOutMessage.getData();
                        bundle.putString("id", clipId);
                        bundle.putInt("period", 1000);

                        handleIncomingMessage(fadeOutMessage);
                        fadeOutMessage.recycle();

                        if (pauseAfterwards) {
                            playerInfo.MediaPlayer.pause();
                            updatePlayerStatus(clipId, StreamState.PAUSED);
                        }
                    }
                } );
        }
    }

    private void fadeInPlayer(final String clipId, final java.util.ArrayList<String> streamsToFadeOut)
    {
        final PlayerInfo playerInfo = mediaPlayers.get(clipId);
        if (playerInfo != null)
        {
            //AsyncTask.execute(new Runnable() {
            //    public void run() {
                    //updatePlayerStatus(playerPath, StreamState.FADING_IN);

                    android.os.Message fadeInMessage = android.os.Message.obtain(
                            null,
                            MessagesToService.FADE_IN.ordinal());
                    android.os.Bundle bundle = fadeInMessage.getData();
                    bundle.putString("id", clipId);
                    bundle.putInt("period", 1000);
                    //bundle.putFloatArray("targetLevel", playerInfo.Volume);
                    bundle.putStringArrayList("streamsToFadeOut", streamsToFadeOut);

                    handleIncomingMessage(fadeInMessage);
                    fadeInMessage.recycle();

                    //updatePlayerStatus(playerPath, StreamState.PLAYING);
            //    }
            //} );
        }
    }



    //////////////////////////////////////////////////////////////////////
    // inner classes
    //////////////////////////////////////////////////////////////////////

    public class IncomingCallInterceptor extends android.content.BroadcastReceiver
    {
        private java.util.ArrayList<String> clipsPausedDueToRing = new java.util.ArrayList<String>();

        public IncomingCallInterceptor() {
            super();
            //android.widget.Toast.makeText(getBaseContext(), "IncomingCallInterceptor instantiated", android.widget.Toast.LENGTH_LONG).show();
        }

        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {                                         // 2
            String state = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE);                         // 3
            String msg = "Phone state changed to " + state;

            if (android.telephony.TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {                                   // 4
                String incomingNumber = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER);  // 5
                msg += ". Incoming number is " + incomingNumber;

                Boolean doAudioFade = (Boolean)(preferences.get("fadeInOut"));
                clipsPausedDueToRing.clear();
                for (String playerClipId : mediaPlayers.keySet()) {
                        PlayerInfo otherPlayerInfo = mediaPlayers.get(playerClipId);
                        if ( (otherPlayerInfo.State == StreamState.QUEUED)
                          || (otherPlayerInfo.State == StreamState.FADING_IN)
                          || (otherPlayerInfo.State == StreamState.PLAYING) )
                        {
                            if (doAudioFade) {
                                // fade out any currently playing audio, then pause it
                                fadeOutPlayer(playerClipId, true);
                            }
                            else {
                                // immediately pause other playing audio
                                otherPlayerInfo.MediaPlayer.pause();
                                updatePlayerStatus(playerClipId, StreamState.PAUSED);
                            }
                            clipsPausedDueToRing.add(playerClipId);
                        }
                }
            }
            else if (android.telephony.TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                Boolean doAudioFade = (Boolean)(preferences.get("fadeInOut"));
                for (String playerClipId : clipsPausedDueToRing) {
                    if (doAudioFade) {
                        fadeInPlayer(playerClipId, null);
                    }
                    else {
                        PlayerInfo playerInfo = mediaPlayers.get(playerClipId);
                        playerInfo.MediaPlayer.pause();
                        updatePlayerStatus(playerClipId, StreamState.PLAYING);
                    }

                }
            }

            //android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show();
        }

    }

    private class MediaPlayerListener implements android.media.MediaPlayer.OnInfoListener,
                                                 android.media.MediaPlayer.OnCompletionListener,
                                                 android.media.MediaPlayer.OnErrorListener,
                                                 android.media.MediaPlayer.OnBufferingUpdateListener,
                                                 android.media.MediaPlayer.OnPreparedListener
    {

        //////////////////////////////////////////////////////////////////////
        // Methods implementing the interface
        //             android.media.MediaPlayer.OnInfoListener
        //////////////////////////////////////////////////////////////////////
        /**
         * Called to indicate an info or a warning.
         *
         * @param mediaPlayer  the MediaPlayer the info pertains to.
         * @param what         the type of info or warning.
         * <ul>
         * <li>MEDIA_INFO_UNKNOWN
         * <li>MEDIA_INFO_VIDEO_TRACK_LAGGING
         * <li>MEDIA_INFO_VIDEO_RENDERING_START
         * <li>MEDIA_INFO_BUFFERING_START
         * <li>MEDIA_INFO_BUFFERING_END
         * <li>MEDIA_INFO_BAD_INTERLEAVING
         * <li>MEDIA_INFO_NOT_SEEKABLE
         * <li>MEDIA_INFO_METADATA_UPDATE
         * <li>MEDIA_INFO_UNSUPPORTED_SUBTITLE
         * <li>MEDIA_INFO_SUBTITLE_TIMED_OUT
         * </ul>
         * @param extra an extra code, specific to the info. Typically
         * implementation dependent.
         * @return True if the method handled the info, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the info to be discarded.
         */
        @Override
        public boolean onInfo(android.media.MediaPlayer mediaPlayer, int what, int extra) {
            String playerClipId = getPlayerClipId(mediaPlayer);
            if (playerClipId != null) {
                android.os.Message message = createMessageToMediaPlayerClients(MessagesToClient.STREAM_INFO_AVAIALABLE, playerClipId);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("what", what);
                messageData.putInt("extra", extra);
                //broadcastMessageToPlayerClients(message);
            }
            return false;
        }

        //////////////////////////////////////////////////////////////////////
        // Methods implementing the interface
        //             android.media.MediaPlayer.OnCompletionListener
        //////////////////////////////////////////////////////////////////////

        /**
         * Called when the end of a media source is reached during playback.
         *
         * @param mediaPlayer the MediaPlayer that reached the end of the file
         */
        @Override
        public void onCompletion(android.media.MediaPlayer mediaPlayer) {
            String playerClipId = getPlayerClipId(mediaPlayer);
            if (playerClipId != null) {
                updatePlayerStatus(playerClipId, StreamState.FINISHED);
                broadcastMessageToPlayerClients(MessagesToClient.PLAYING_STREAM_COMPLETE, playerClipId);
                mediaPlayers.remove(playerClipId);

                try {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception exception) {
                    android.util.Log.d(LOG_TAG, "Error destroying media player: " + exception);
                } finally {
                    mediaPlayer = null;
                }

                // in case we previously set the nextClipQueued to '1', here we restore it to 0 so
                // that when the currenly playing clip nears it's end, it will be able to queue yet
                // another
                nextClipQueued = 0;
            }
        }

        //////////////////////////////////////////////////////////////////////
        // Methods implementing the interface
        //             android.media.MediaPlayer.OnErrorListener
        //////////////////////////////////////////////////////////////////////

        /**
         * Called to indicate an error.
         *
         * @param mediaPlayer  the MediaPlayer the error pertains to
         * @param what         the type of error that has occurred:
         * <ul>
         * <li>MEDIA_ERROR_UNKNOWN
         * <li>MEDIA_ERROR_SERVER_DIED
         * </ul>
         * @param extra an extra code, specific to the error. Typically
         * implementation dependent.
         * <ul>
         * <li>MEDIA_ERROR_IO
         * <li>MEDIA_ERROR_MALFORMED
         * <li>MEDIA_ERROR_UNSUPPORTED
         * <li>MEDIA_ERROR_TIMED_OUT
         * </ul>
         * @return True if the method handled the error, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the OnCompletionListener to be called.
         */
        public boolean onError(android.media.MediaPlayer mediaPlayer, int what, int extra) {
            //android.widget.Toast.makeText(this, "music player failed", android.widget.Toast.LENGTH_SHORT).show();
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception exception) {
                    android.util.Log.d(LOG_TAG, "Error destroying media player: " + exception);
                } finally {
                    mediaPlayer = null;
                }
            }

            String playerClipId = getPlayerClipId(mediaPlayer);
            if (playerClipId != null) {
                updatePlayerStatus(playerClipId, StreamState.ERROR);
                android.os.Message message = createMessageToMediaPlayerClients(MessagesToClient.ERROR_PLAYING_STREAM, playerClipId);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("what", what);
                messageData.putInt("extra", extra);
                broadcastMessageToPlayerClients(message);

                mediaPlayers.remove(playerClipId);
            }
            return false;
        }


        //////////////////////////////////////////////////////////////////////
        // Methods implementing the interface
        //        android.media.MediaPlayer.OnBufferingUpdateListener
        //////////////////////////////////////////////////////////////////////

        /**
         * Called to update status in buffering a media stream received through
         * progressive HTTP download. The received buffering percentage
         * indicates how much of the content has been buffered or played.
         * For example a buffering update of 80 percent when half the content
         * has already been played indicates that the next 30 percent of the
         * content to play has been buffered.
         *
         * @param mediaPlayer  the MediaPlayer the update pertains to
         * @param percent      the percentage (0-100) of the content
         *                     that has been buffered or played thus far
         */
        @Override
        public void onBufferingUpdate(android.media.MediaPlayer mediaPlayer, int percent) {
            final String playerClipId = getPlayerClipId(mediaPlayer);
            if (playerClipId != null) {
                PlayerInfo playerInfo = mediaPlayers.get(playerClipId);
                int currentPosition = mediaPlayer.getCurrentPosition();
                //android.util.Log.d(LOG_TAG, "current position=" + (currentPosition/1000.0f));
                android.os.Message message = createMessageToMediaPlayerClients(MessagesToClient.BUFFERING_UPDATE, playerClipId);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("percent", percent);
                messageData.putInt("position", currentPosition);
                broadcastMessageToPlayerClients(message);

                if ( (percent == 100)
                  && (playerInfo.State == StreamState.QUEUED))
                {
                    Boolean doAudioFade = (Boolean)(preferences.get("fadeInOut"));
                    java.util.ArrayList<String> streamsToFadeOut = new java.util.ArrayList<String>();
                    if (preferences.get("oneClipAtATime") == Boolean.TRUE) {
                        // if any other streams are playing, pause them
                        for (String otherPlayerClipId : mediaPlayers.keySet()) {
                            if ( !(otherPlayerClipId.equals(playerClipId)) ) {
                                PlayerInfo otherPlayerInfo = mediaPlayers.get(otherPlayerClipId);
                                if ( (otherPlayerInfo.State == StreamState.FADING_IN)
                                  || (otherPlayerInfo.State == StreamState.PLAYING)
                                  || (otherPlayerInfo.State == StreamState.FADING_OUT_PRIOR_TO_PAUSE) )
                                {
                                    if (doAudioFade) {
                                        // fade out any currently playing audio, then pause it
                                        //fadeOutPlayer(otherPlayerPath, true);
                                        streamsToFadeOut.add(otherPlayerClipId);
                                    }
                                    else {
                                        // immediately pause other playing audio
                                        otherPlayerInfo.MediaPlayer.pause();
                                        updatePlayerStatus(otherPlayerClipId, StreamState.PAUSED);
                                    }
                                }
                            }
                        }
                    }

                    if (doAudioFade) {
                        // fade in the current stream
                        fadeInPlayer(playerClipId, streamsToFadeOut);
                    }
                    else {
                        updatePlayerStatus(playerClipId, StreamState.PLAYING);
                    }
                } // if ( (percent == 100)
                  //   && (playerInfo.State == StreamState.QUEUED))

                else {

                    int clipDuration = mediaPlayer.getDuration();
                    if ( (clipDuration != 0)
                      && (currentPosition > (clipDuration - 2000)) )
                    {
                    if ( (queuedIds != null)
                      && (queuedIds.size() > 0)
                      && (nextClipQueued == 0))
                    {
                        if (preferences.get("autoplaynext") == Boolean.TRUE) {
                            android.util.Log.d(LOG_TAG, "onBufferingUpdate() - starting next clip...");
                            final PlayerInfo thisStreamState = mediaPlayers.get(playerClipId);
                            android.os.AsyncTask.execute(
                                    new Runnable() {
                                        public void run() {
                                            String clipId = queuedIds.get(0);
                                            String clipUrl = queuedUrls.get(0);
                                            String clipTitle = queuedTitles.get(0);
                                            queuedIds.remove(0);
                                            queuedUrls.remove(0);
                                            queuedTitles.remove(0);

                                            android.os.Message playMessage = android.os.Message.obtain(
                                                    null,
                                                    MediaPlayerService.MessagesToService.PLAY_STREAM.ordinal());
                                            android.os.Bundle bundle = playMessage.getData();
                                            bundle.putString("id", clipId);
                                            bundle.putString("url", clipUrl);
                                            bundle.putString("title", clipTitle);
                                            bundle.putString("trackContext", thisStreamState.ClipContext);
                                            bundle.putFloat("volume", 1.0f);
                                            bundle.putStringArrayList("queuedIds", queuedIds);
                                            bundle.putStringArrayList("queuedUrls", queuedUrls);
                                            bundle.putStringArrayList("queuedTitles", queuedTitles);

                                            handleIncomingMessage(playMessage);
                                            playMessage.recycle();
                                        }
                                    });

                            nextClipQueued = 1;
                        } // if (preferences.get("autoplaynext") == Boolean.TRUE)

                        // only fade out the current stream if we're not automatically playing another stream
                        else if (preferences.get("fadeInOut") == Boolean.TRUE) {
                            fadeOutPlayer(playerClipId, false);
                        }
                    }
                    }
                }
            }
        }

        //////////////////////////////////////////////////////////////////////
        // Methods implementing the interface
        //        android.media.MediaPlayer.OnPreparedListener
        //////////////////////////////////////////////////////////////////////

        /**
         * Called when the media file is ready for playback.
         *
         * @param mediaPlayer the MediaPlayer that is ready for playback
         */
        @Override
        public void onPrepared ( MediaPlayer mediaPlayer ) {
            String playerClipId = getPlayerClipId(mediaPlayer);
            if (playerClipId != null) {
                int duration = mediaPlayer.getDuration();

                android.os.Message message = createMessageToMediaPlayerClients(MessagesToClient.READY_FOR_PLAYBACK, playerClipId);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("duration", duration);
                broadcastMessageToPlayerClients(message);
            }

        }
    }

}
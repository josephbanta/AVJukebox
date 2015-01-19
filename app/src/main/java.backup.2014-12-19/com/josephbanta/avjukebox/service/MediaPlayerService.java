package com.josephbanta.avjukebox.service;

import android.content.Intent;
import android.media.MediaPlayer;

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

    public enum MessagesToService {
        REGISTER_CLIENT,
        DEREGISTER_CLIENT,
        PLAY_STREAM,
        FADE_IN,
        FADE_OUT,
        SCHEDULED_FADE_OUT,
        PAUSE_STREAM,
        STOP_STREAM,
    }

    public enum MessagesToClient {
        UNRECOGNIZED_REQUEST,
        READY_FOR_PLAYBACK,
        ERROR_PLAYING_STREAM,
        STREAM_INFO_AVAIALABLE,
        BUFFERING_UPDATE,
        PLAYING_STREAM_COMPLETE,
        FADE_IN_COMPLETED,
        FADE_OUT_COMPLETED
    }

    // member variables
    private android.app.NotificationManager mNotificationManager; // For showing and hiding our notification.

    // Keeps track of all current registered clients.
    private java.util.ArrayList<android.os.Messenger> mClients = new java.util.ArrayList<android.os.Messenger>();

    private java.util.HashMap<String, android.media.MediaPlayer> mediaPlayers;
    private boolean isPlaying = false;
    private MediaPlayerListener mediaPlayerListener = new MediaPlayerListener();


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

        mediaPlayers = new java.util.HashMap<String, android.media.MediaPlayer>();
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

        for (android.media.MediaPlayer player : mediaPlayers.values()) {
            if (player != null) {
                try {
                    player.stop();
                    player.release();
                } catch (Exception exception) {
                    android.util.Log.d(LOG_TAG, "Error destroying media player: " + exception);
                } finally {
                    player = null;
                }
            }
        }
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
        }
        else if (msg.what == MessagesToService.DEREGISTER_CLIENT.ordinal()) {
            mClients.remove(msg.replyTo);
        }
        else if (msg.what == MessagesToService.PLAY_STREAM.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            String path = messageData.getString("path");
            String title = messageData.getString("title");
            float volume = messageData.getFloat("volume");
            playStream(path, title, volume);
        }
        else if (msg.what == MessagesToService.FADE_IN.ordinal()) {
            android.os.Bundle messageData = msg.getData();
            String path = messageData.getString("path");

            android.util.Log.d(LOG_TAG, "handleIncomingMessage(FADE_IN)");
            final android.media.MediaPlayer thisPlayer = mediaPlayers.get(path);
            if (thisPlayer != null) {
                //AsyncTask.execute(
                //        new Runnable() {
                //            @Override
                //            public void run() {
                                for (int currentStep=0, numSteps=10; currentStep<numSteps; currentStep++) {
                                    if (currentStep != 0) {try {java.lang.Thread.sleep(100, 0);} catch (InterruptedException ie) {}}
                                    float logVol = 1 - (float) (Math.log(numSteps - currentStep) / Math.log(numSteps));
                                    android.util.Log.d(LOG_TAG, "handleIncomingMessage(FADE_IN) - setting volume to " + logVol);
                                    thisPlayer.setVolume(logVol, logVol);
                                }
                //            }
                //        } );
/*
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        for (int currentStep=0, numSteps=10; currentStep<numSteps; currentStep++) {
                            if (currentStep != 0) {try {java.lang.Thread.sleep(100, 0);} catch (InterruptedException ie) {}}
                            float logVol = 1 - (float) (Math.log(numSteps - currentStep) / Math.log(numSteps));
                            thisPlayer.setVolume(logVol, logVol);
                        }

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);

                    }
                }.execute();
*/
            }
        }
        else if ( (msg.what == MessagesToService.SCHEDULED_FADE_OUT.ordinal())
               || (msg.what == MessagesToService.FADE_OUT.ordinal()) )
        {
            android.os.Bundle messageData = msg.getData();
            String path = messageData.getString("path");
            final int delayMilliseconds = (msg.what == MessagesToService.SCHEDULED_FADE_OUT.ordinal()) ? messageData.getInt("after") : 0;
            final android.media.MediaPlayer thisPlayer = mediaPlayers.get(path);

            if (thisPlayer != null) {
                //AsyncTask.execute(new Runnable() {
                //        @Override
                //        public void run() {
                            for (int currentStep = 0, numSteps = 10; currentStep < numSteps; currentStep++) {
                                if (currentStep == 0) {
                                    if (delayMilliseconds != 0) {
                                        try {
                                            java.lang.Thread.sleep(delayMilliseconds, 0);
                                        } catch (InterruptedException ie) {}
                                    }
                                }
                                else {
                                    try {
                                        java.lang.Thread.sleep(100, 0);
                                    } catch (InterruptedException ie) {}
                                }
                                float logVol = (float) (Math.log(numSteps - currentStep) / Math.log(numSteps));
                                thisPlayer.setVolume(logVol, logVol);
                            }
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

    /**
     * Show a notification while this service is running.
     */
    private void showNotification (String text) {

        // The PendingIntent to launch our activity if the user selects this notification
        android.app.PendingIntent contentIntent
                = android.app.PendingIntent.getActivity( this,
                                                         0,
                                                         new android.content.Intent(this, TrackListActivity.class),
                                                         0 );

        android.app.Notification notification = new android.app.Notification.Builder(this)
                .setContentTitle(getText(R.string.app_name)) //"New mail from " + sender.toString())
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setSmallIcon(com.josephbanta.avjukebox.R.drawable.ic_launcher)
                //.setLargeIcon(aBitmap)
                .getNotification();

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNotificationManager.notify(NotificationIds.PLAYING_STREAM.ordinal(), notification);
    }

    private String getPlayerPath (android.media.MediaPlayer mediaPlayer)
    {
        String thisPlayerPath = null;
        for (String path : mediaPlayers.keySet()) {
            android.media.MediaPlayer thatPathPlayer = mediaPlayers.get(path);
            if (mediaPlayer.equals(thatPathPlayer)) {
                thisPlayerPath = path;
            }
        }
        return thisPlayerPath;
    }

    public void playStream (String path, String title, float volume) {

        android.media.MediaPlayer player = new android.media.MediaPlayer();
        player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
        player.setOnErrorListener(mediaPlayerListener);
        player.setOnCompletionListener(mediaPlayerListener);
        player.setOnInfoListener(mediaPlayerListener);
        player.setOnBufferingUpdateListener(mediaPlayerListener);
        player.setOnPreparedListener(mediaPlayerListener);
        player.setVolume(volume, volume);

        //if (player != null) {
        //    player.setLooping(true);
        //    //mPlayer.setVolume(100,100);
        //}

        android.util.Log.i("", "PlayService will play " + path);
        try {
            if (player != null) {
                player.reset();
                player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
                player.setDataSource(path);
                if (title != null) {
                    showNotification(title);
                }
                player.setLooping(false);
                player.prepare();
                android.util.Log.d("logtag", "PlayService player.prepare() returned");
                player.start();
//                CustomNotification();

                mediaPlayers.put(path, player);

                isPlaying = true;
                android.util.Log.i("logtag", "player.start() returned");
            } else {
                android.util.Log.i("logtag", "mediaplayer null");
            }
            //updateNotification(true);
        } catch (java.io.IOException e) {
            android.util.Log.e("", "PlayService::onStart() IOException attempting player.prepare()\n");
            android.widget.Toast t = android.widget.Toast.makeText(getApplicationContext(), "PlayService was unable to start playing recording: " + e, android.widget.Toast.LENGTH_LONG);
            t.show();
            // return;
        } catch (java.lang.Exception e) {
            android.widget.Toast t = android.widget.Toast.makeText(getApplicationContext(), "MusicPlayer was unable to start playing recording: " + e, android.widget.Toast.LENGTH_LONG);
            t.show();

            android.util.Log.e("", "PlayService::onStart caught unexpected exception", e);
        }

    }
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

    private android.os.Message createMessageToMediaPlayerCients ( MediaPlayerService.MessagesToClient messageType )
    {
        return createMessageToMediaPlayerCients(messageType, null);
    }

    private android.os.Message createMessageToMediaPlayerCients ( MediaPlayerService.MessagesToClient messageType,
                                                                  String messageString )
    {
        android.os.Message msg = android.os.Message.obtain(
                null,
                messageType.ordinal(), 0, 0);

        if (messageString != null) {
            android.os.Bundle messageData = msg.getData();
            messageData.putString("message", messageString);
        }

        return msg;
    }

    private void broadcastMessageToPlayerClients (MediaPlayerService.MessagesToClient messageType)
            //throws android.os.RemoteException,
            //java.io.NotActiveException
    {
        broadcastMessageToPlayerClients(createMessageToMediaPlayerCients(messageType));
    }

    private void broadcastMessageToPlayerClients (MediaPlayerService.MessagesToClient messageType, String messageString)
            //throws android.os.RemoteException,
            //java.io.NotActiveException
    {
        broadcastMessageToPlayerClients(createMessageToMediaPlayerCients(messageType, messageString));
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
/*
                    android.os.Message message = android.os.Message.obtain(null, messageType.ordinal(), 0, 0);
                    android.os.Bundle messageBundle = message.getData();
                    messageBundle.putString("message", messageString);
                    if (value1 != null) {
                        messageBundle.putInt("value1", value1.intValue());
                    }
                    if (value2 != null) {
                        messageBundle.putInt("value2", value2.intValue());
                    }
*/
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



    //////////////////////////////////////////////////////////////////////
    // inner classes
    //////////////////////////////////////////////////////////////////////

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
            String playerPath = getPlayerPath(mediaPlayer);
            if (playerPath != null) {
                android.os.Message message = createMessageToMediaPlayerCients(MessagesToClient.STREAM_INFO_AVAIALABLE, playerPath);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("what", what);
                messageData.putInt("extra", extra);
                broadcastMessageToPlayerClients(message);
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
            mNotificationManager.cancel(NotificationIds.PLAYING_STREAM.ordinal());

            String playerPath = getPlayerPath(mediaPlayer);
            if (playerPath != null) {
                broadcastMessageToPlayerClients(MessagesToClient.PLAYING_STREAM_COMPLETE, playerPath);
                mediaPlayers.remove(playerPath);

                try {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception exception) {
                    android.util.Log.d(LOG_TAG, "Error destroying media player: " + exception);
                } finally {
                    mediaPlayer = null;
                }
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

            String playerPath = getPlayerPath(mediaPlayer);
            if (playerPath != null) {
                android.os.Message message = createMessageToMediaPlayerCients(MessagesToClient.ERROR_PLAYING_STREAM, playerPath);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("what", what);
                messageData.putInt("extra", extra);
                broadcastMessageToPlayerClients(message);

                mediaPlayers.remove(playerPath);
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
            String playerPath = getPlayerPath(mediaPlayer);
            if (playerPath != null) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                android.util.Log.d(LOG_TAG, "current position=" + (currentPosition/1000.0f));
                android.os.Message message = createMessageToMediaPlayerCients(MessagesToClient.BUFFERING_UPDATE, playerPath);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("percent", percent);
                broadcastMessageToPlayerClients(message);
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
            String playerPath = getPlayerPath(mediaPlayer);
            if (playerPath != null) {
                int duration = mediaPlayer.getDuration();

                android.os.Message message = createMessageToMediaPlayerCients(MessagesToClient.READY_FOR_PLAYBACK, playerPath);
                android.os.Bundle messageData = message.getData();
                messageData.putInt("duration", duration);
                broadcastMessageToPlayerClients(message);
            }

        }
    }

}
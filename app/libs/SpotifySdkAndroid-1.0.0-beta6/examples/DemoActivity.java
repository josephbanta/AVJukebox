/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.sdk.demo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.spotify.sdk.android.Spotify;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.authentication.SpotifyAuthentication;
import com.spotify.sdk.android.playback.Config;
import com.spotify.sdk.android.playback.ConnectionStateCallback;
import com.spotify.sdk.android.playback.Connectivity;
import com.spotify.sdk.android.playback.Player;
import com.spotify.sdk.android.playback.PlayerNotificationCallback;
import com.spotify.sdk.android.playback.PlayerState;
import com.spotify.sdk.android.playback.PlayerStateCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DemoActivity extends ActionBarActivity implements
        PlayerNotificationCallback, ConnectionStateCallback {
    //   ____                _              _
    //  / ___|___  _ __  ___| |_ __ _ _ __ | |_ ___
    // | |   / _ \| '_ \/ __| __/ _` | '_ \| __/ __|
    // | |__| (_) | | | \__ \ || (_| | | | | |_\__ \
    //  \____\___/|_| |_|___/\__\__,_|_| |_|\__|___/
    //

    @SuppressWarnings("SpellCheckingInspection")
    private static final String CLIENT_ID = "8a91678afa49446c9aff1beaabe9c807";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String REDIRECT_URI = "testschema://callback";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String TEST_SONG_URI = "spotify:track:6KywfgRqvgvfJc3JRwaZdZ";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String TEST_SONG_MONO_URI = "spotify:track:1FqY3uJypma5wkYw66QOUi";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String TEST_SONG_48kHz_URI = "spotify:track:3wxTNS3aqb9RbBLZgJdZgH";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String TEST_PLAYLIST_URI = "spotify:user:sqook:playlist:0BZvnsfuqmnLyj6WVRuSte";
    @SuppressWarnings("SpellCheckingInspection")
    private static final String TEST_QUEUE_SONG_URI = "spotify:track:5EEOjaJyWvfMglmEwf9bG3";

    // Currently the way to play the album is to resolve the list of tracks using
    // WebAPI (as shown here https://developer.spotify.com/web-api/get-albums-tracks/)
    // and pass it to Player#playTrackList(java.util.List)
    // The list of tracks below is from the album: spotify:album:4JWoGR0Kwa0DlqbikKNqOc
    @SuppressWarnings("SpellCheckingInspection")
    private static final List<String> TEST_ALBUM_TRACKS = Arrays.asList(
            "spotify:track:2To3PTOTGJUtRsK3nQemP4",
            "spotify:track:0tDoBMgyAzGgLhs73KPrJL",
            "spotify:track:5YkSQuB8i7J4TTyj0xw6ol",
            "spotify:track:3WpLfCkrlQxj8SISLzhs06",
            "spotify:track:2lGNTC3NKCG1d4lR8x3611",
            "spotify:track:0kdSj5REwpHjTBaBsm1wv8",
            "spotify:track:3BgnZiGnnRlXfeGR8ryKzT",
            "spotify:track:00cVWQIFyQnIdsgoVy7qAG",
            "spotify:track:6eEEoowHpnaD3q83ZhYmhZ",
            "spotify:track:1HFBn8S30ndZ7lLb9HbENU",
            "spotify:track:1I9VibKgJTqGfrh8fEK3sL",
            "spotify:track:6rXSPMgGIyOYiMhsj3eSAi",
            "spotify:track:2xwuXthwdNGbPyEqifPQNW",
            "spotify:track:5vRuWI48vKn4TV7efrYtJL",
            "spotify:track:4SEDYSBDd4Ota125LjHa2w",
            "spotify:track:2bVTnSTjLWAizyj4XcU5bf",
            "spotify:track:4gQzqlFuqv6l4Ka633Ue7T",
            "spotify:track:0SLVmM7IrrtkPNa1Fi3IKT"
    );

    /**
     * UI controls which may only be enabled after the player has been initialized,
     * (or effectively, after the user has logged in).
     */
    private static final int[] REQUIRES_INITIALIZED_STATE = {
            R.id.show_player_state_button,
            R.id.play_track_button,
            R.id.play_mono_track_button,
            R.id.play_48khz_track_button,
            R.id.play_album_button,
            R.id.play_playlist_button,
            R.id.pause_button,
            R.id.seek_button
    };

    /**
     * UI controls which should only be enabled if the player is actively playing.
     */
    private static final int[] REQUIRES_PLAYING_STATE = {
            R.id.skip_next_button,
            R.id.skip_prev_button,
            R.id.queue_song_button,
            R.id.toggle_shuffle_button,
            R.id.toggle_repeat_button
    };

    //  _____ _      _     _
    // |  ___(_) ___| | __| |___
    // | |_  | |/ _ \ |/ _` / __|
    // |  _| | |  __/ | (_| \__ \
    // |_|   |_|\___|_|\__,_|___/
    //

    /**
     * The player used by this activity. There is only ever one instance of the player,
     * which is owned by the {@link com.spotify.sdk.android.Spotify} class and refcounted.
     * This means that you may use the Player from as many Fragments as you want, and be
     * assured that state remains consistent between them.
     * <p/>
     * However, each fragment, activity, or helper class <b>must</b> call
     * {@link com.spotify.sdk.android.Spotify#destroyPlayer(Object)} when they are no longer
     * need that player. Failing to do so will result in leaked resources.
     */
    private Player mPlayer;

    private PlayerState mCurrentPlayerState = new PlayerState();

    /**
     * Used to get notifications from the system about the current network state in order
     * to pass them along to
     * {@link com.spotify.sdk.android.playback.Player#setConnectivityStatus(com.spotify.sdk.android.playback.Connectivity)}.
     * Note that this implies <pre>android.permission.ACCESS_NETWORK_STATE</pre> must be
     * declared in the manifest. Not setting the correct network state in the SDK may
     * result in strange behavior.
     */
    private BroadcastReceiver mNetworkStateReceiver;

    /**
     * Used to log messages to a {@link android.widget.TextView} in this activity.
     */
    private TextView mStatusText;
    /**
     * Used to scroll the {@link #mStatusText} to the bottom after updating text.
     */
    private ScrollView mStatusTextScrollView;

    //  ___       _ _   _       _ _          _   _
    // |_ _|_ __ (_) |_(_) __ _| (_)______ _| |_(_) ___  _ __
    //  | || '_ \| | __| |/ _` | | |_  / _` | __| |/ _ \| '_ \
    //  | || | | | | |_| | (_| | | |/ / (_| | |_| | (_) | | | |
    // |___|_| |_|_|\__|_|\__,_|_|_/___\__,_|\__|_|\___/|_| |_|
    //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);

        // Get a reference to any UI widgets that we'll need to use later
        mStatusText = (TextView) findViewById(R.id.status_text);
        mStatusTextScrollView = (ScrollView) findViewById(R.id.status_text_container);

        updateButtons();
        logStatus("Ready");
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set up the broadcast receiver for network events. Note that we also unregister
        // this receiver again in onPause().
        mNetworkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mPlayer != null) {
                    Connectivity connectivity = getNetworkConnectivity(getBaseContext());
                    logStatus("Network state changed: " + connectivity.toString());
                    mPlayer.setConnectivityStatus(connectivity);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkStateReceiver, filter);

        if (mPlayer != null) {
            mPlayer.addPlayerNotificationCallback(DemoActivity.this);
            mPlayer.addConnectionStateCallback(DemoActivity.this);
        }
    }

    /**
     * Registering for connectivity changes in Android does not actually deliver them to
     * us in the delivered intent.
     *
     * @param context Android context
     * @return Connectivity state to be passed to the SDK
     * @see com.spotify.sdk.android.playback.Player#setConnectivityStatus(com.spotify.sdk.android.playback.Connectivity)
     */
    //TODO(nik): ^-- is that actually true? I think so, but someone ought to look it up...
    private Connectivity getNetworkConnectivity(Context context) {
        ConnectivityManager connectivityManager;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null) {
            return Connectivity.fromNetworkType(activeNetwork.getType());
        } else {
            return Connectivity.OFFLINE;
        }
    }

    //     _         _   _                _   _           _   _
    //    / \  _   _| |_| |__   ___ _ __ | |_(_) ___ __ _| |_(_) ___  _ __
    //   / _ \| | | | __| '_ \ / _ \ '_ \| __| |/ __/ _` | __| |/ _ \| '_ \
    //  / ___ \ |_| | |_| | | |  __/ | | | |_| | (_| (_| | |_| | (_) | | | |
    // /_/   \_\__,_|\__|_| |_|\___|_| |_|\__|_|\___\__,_|\__|_|\___/|_| |_|
    //

    private void openLoginWindow() {
        SpotifyAuthentication.openAuthWindow(CLIENT_ID, "token", REDIRECT_URI,
                new String[]{"user-read-private", "playlist-read", "playlist-read-private", "streaming"},
                null, this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        // In the above call to openLoginWindow(), we prompt the system to log the user in
        // with the default web browser. When the user has finished logging in, then we will
        // get a call to the REDIRECT_URI, which will in turn be picked up here.
        // TODO(nik): Should probably make sure that this doesn't crash if some other intent ends up here
        Uri uri = intent.getData();
        if (uri != null) {
            onAuthenticationComplete(SpotifyAuthentication.parseOauthResponse(uri));
        }
    }

    private void onAuthenticationComplete(AuthenticationResponse authResponse) {
        // Once we have obtained an authorization token, we can proceed with creating a Player.
        logStatus("Got authentication token");
        Spotify spotify = new Spotify();
        Config playerConfig = new Config(getApplicationContext(), authResponse.getAccessToken(), CLIENT_ID);
        // Since the Player is a static singleton owned by the Spotify class, we pass "this" as
        // the second argument in order to refcount it properly. Note that the method
        // Spotify.destroyPlayer() also takes an Object argument, which must be the same as the
        // one passed in here. If you pass different instances to Spotify.getPlayer() and
        // Spotify.destroyPlayer(), that will definitely result in resource leaks.
        mPlayer = spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized() {
                        logStatus("-- Player initialized --");
                        mPlayer.setConnectivityStatus(getNetworkConnectivity(DemoActivity.this));
                        mPlayer.addPlayerNotificationCallback(DemoActivity.this);
                        mPlayer.addConnectionStateCallback(DemoActivity.this);
                        // Trigger UI refresh
                        updateButtons();
                    }

                    @Override
                    public void onError(Throwable error) {
                        logStatus("Error in initialization: " + error.getMessage());
                    }
                });
    }

    //  _   _ ___   _____                 _
    // | | | |_ _| | ____|_   _____ _ __ | |_ ___
    // | | | || |  |  _| \ \ / / _ \ '_ \| __/ __|
    // | |_| || |  | |___ \ V /  __/ | | | |_\__ \
    //  \___/|___| |_____| \_/ \___|_| |_|\__|___/
    //

    private void updateButtons() {
        boolean initialized = mPlayer != null && mPlayer.isInitialized();

        // Login button should be the inverse of the initialized state
        findViewById(R.id.login_button).setEnabled(!initialized);

        // Set enabled for all widgets which depend on initialized state
        for (int id : REQUIRES_INITIALIZED_STATE) {
            findViewById(id).setEnabled(initialized);
        }

        // Same goes for the playing state
        boolean playing = initialized && mCurrentPlayerState.playing;
        for (int id : REQUIRES_PLAYING_STATE) {
            findViewById(id).setEnabled(playing);
        }
    }

    public void onLoginButtonClicked(View view) {
        logStatus("Logging in");
        openLoginWindow();
    }

    public void onShowPlayerStateButtonClicked(View view) {
        mPlayer.getPlayerState(new PlayerStateCallback() {
            @Override
            public void onPlayerState(PlayerState playerState) {
                logStatus("-- Current player state --");
                logStatus("Playing? " + playerState.playing);
                logStatus("Position: " + playerState.positionInMs + "ms");
                logStatus("Shuffling? " + playerState.shuffling);
                logStatus("Repeating? " + playerState.repeating);
                logStatus("Active device? " + playerState.activeDevice);
                logStatus("Track uri: " + playerState.trackUri);
                logStatus("Track duration: " + playerState.durationInMs);
            }
        });
    }

    public void onPlayButtonClicked(View view) {
        if (view.getId() == R.id.play_album_button) {
            logStatus("Starting playback the list of tracks");
            mPlayer.play(TEST_ALBUM_TRACKS);
        } else {
            String uri;
            switch (view.getId()) {
                case R.id.play_track_button:
                    uri = TEST_SONG_URI;
                    break;
                case R.id.play_mono_track_button:
                    uri = TEST_SONG_MONO_URI;
                    break;
                case R.id.play_48khz_track_button:
                    uri = TEST_SONG_48kHz_URI;
                    break;
                case R.id.play_playlist_button:
                    uri = TEST_PLAYLIST_URI;
                    break;
                default:
                    throw new IllegalArgumentException("View ID does not have an associated URI to play");
            }

            logStatus("Starting playback for " + uri);
            mPlayer.play(uri);
        }
    }

    public void onPauseButtonClicked(View view) {
        if (mCurrentPlayerState.playing) {
            mPlayer.pause();
        } else {
            mPlayer.resume();
        }
    }

    public void onSkipToPreviousButtonClicked(View view) {
        mPlayer.skipToPrevious();
    }

    public void onSkipToNextButtonClicked(View view) {
        mPlayer.skipToNext();
    }

    public void onQueueSongButtonClicked(View view) {
        mPlayer.queue(TEST_QUEUE_SONG_URI);
        Toast toast = Toast.makeText(this, R.string.song_queued_toast, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void onToggleShuffleButtonClicked(View view) {
        mPlayer.setShuffle(!mCurrentPlayerState.shuffling);
    }

    public void onToggleRepeatButtonClicked(View view) {
        mPlayer.setRepeat(!mCurrentPlayerState.repeating);
    }

    public void onSeekButtonClicked(View view) {
        // Skip to 10 seconds in the current song
        mPlayer.seekToPosition(10000);
    }

    //   ____      _ _ _                _      __  __      _   _               _
    //  / ___|__ _| | | |__   __ _  ___| | __ |  \/  | ___| |_| |__   ___   __| |___
    // | |   / _` | | | '_ \ / _` |/ __| |/ / | |\/| |/ _ \ __| '_ \ / _ \ / _` / __|
    // | |__| (_| | | | |_) | (_| | (__|   <  | |  | |  __/ |_| | | | (_) | (_| \__ \
    //  \____\__,_|_|_|_.__/ \__,_|\___|_|\_\ |_|  |_|\___|\__|_| |_|\___/ \__,_|___/
    //

    @Override
    public void onLoggedIn() {
        logStatus("Login complete");
    }

    @Override
    public void onLoggedOut() {
        logStatus("Logout complete");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        logStatus("Login error");
    }

    @Override
    public void onTemporaryError() {
        logStatus("Temporary error occurred");
    }

    @Override
    public void onNewCredentials(String credentialsBlob) {
        logStatus("Received credentials blob");
    }

    @Override
    public void onConnectionMessage(final String message) {
        logStatus("Incoming connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(final EventType eventType, final PlayerState playerState) {
        // Remember kids, always use the English locale when changing case for non-UI strings!
        // Otherwise you'll end up with mysterious errors when running in the Turkish locale.
        // See: http://java.sys-con.com/node/46241
        String eventName = eventType.name().toLowerCase(Locale.ENGLISH).replaceAll("_", " ");
        logStatus("Player event: " + eventName);
        mCurrentPlayerState = playerState;
        updateButtons();
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        String errorName = errorType.name().toLowerCase(Locale.ENGLISH).replaceAll("_", " ");
        logStatus("Player error: " + errorName);
    }

    //  _____                       _   _                 _ _ _
    // | ____|_ __ _ __ ___  _ __  | | | | __ _ _ __   __| | (_)_ __   __ _
    // |  _| | '__| '__/ _ \| '__| | |_| |/ _` | '_ \ / _` | | | '_ \ / _` |
    // | |___| |  | | | (_) | |    |  _  | (_| | | | | (_| | | | | | | (_| |
    // |_____|_|  |_|  \___/|_|    |_| |_|\__,_|_| |_|\__,_|_|_|_| |_|\__, |
    //                                                                 |___/

    /**
     * Print a status message from a callback (or some other place) to the TextView in this
     * activity
     *
     * @param status Status message
     */
    private void logStatus(String status) {
        Log.i("SpotifySdkDemo", status);
        if (!TextUtils.isEmpty(mStatusText.getText())) {
            mStatusText.append("\n");
        }
        mStatusText.append(status);
        mStatusTextScrollView.post(new Runnable() {
            @Override
            public void run() {
                // Scroll to the bottom
                mStatusTextScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    //  ____            _                   _   _
    // |  _ \  ___  ___| |_ _ __ _   _  ___| |_(_) ___  _ __
    // | | | |/ _ \/ __| __| '__| | | |/ __| __| |/ _ \| '_ \
    // | |_| |  __/\__ \ |_| |  | |_| | (__| |_| | (_) | | | |
    // |____/ \___||___/\__|_|   \__,_|\___|\__|_|\___/|_| |_|
    //

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mNetworkStateReceiver);

        // Note that calling Spotify.destroyPlayer() will also remove any callbacks on whatever
        // instance was passed as the refcounted owner. So in the case of this particular example,
        // it's not strictly necessary to call these methods, however it is generally good practice
        // and also will prevent your application from doing extra work in the background when
        // paused.
        if (mPlayer != null) {
            mPlayer.removePlayerNotificationCallback(DemoActivity.this);
            mPlayer.removeConnectionStateCallback(DemoActivity.this);
        }
    }

    @Override
    protected void onDestroy() {
        // *** ULTRA-IMPORTANT ***
        // ALWAYS call this in your onDestroy() method, otherwise you will leak native resources!
        // This is an unfortunate necessity due to the different memory management models of
        // Java's garbage collector and C++ RAII.
        // For more information, see the documentation on Spotify.destroyPlayer().
        Spotify.destroyPlayer(this);
        super.onDestroy();
    }
}

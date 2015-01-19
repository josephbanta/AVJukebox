package com.josephbanta.avjukebox;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.josephbanta.avjukebox.service.SpeechRecognizerService;
import com.spotify.sdk.android.Spotify;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.authentication.SpotifyAuthentication;
import com.spotify.sdk.android.playback.Player;
import com.spotify.sdk.android.playback.PlayerState;


public class MainActivity extends android.app.Activity
                       implements com.spotify.sdk.android.playback.PlayerNotificationCallback,
                                  com.spotify.sdk.android.playback.ConnectionStateCallback
{
    // inner classes
    private enum ActivityState {
        WAITING_FOR_INPUT,
        PARTIAL_INPUT_RECIEVED,
        INPUT_COMPLETE__SEARCHING_FOR_TRACKS,
        INPUT_COMPLETE__SEARCH_COMPLETE,
        INACTIVE
    }


    // constants
    public final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final String CLIENT_ID = "fbf79c09869448c59bf950609506d54f";
    private static final String REDIRECT_URI = "avjukebox://callback";


    // member variables
    private android.view.ViewGroup mainViewGroup;
    private android.widget.TextView mainTextView;
    private android.widget.TextView hintTextView;
    private android.widget.TextView whatsHappeningTextView;

    private com.spotify.sdk.android.playback.Player mPlayer;

    private String trackId = null;

    private ActivityState activityState;

    /** Messenger for communicating with the service. */
    android.os.Messenger speechRecognizerServiceMessageSender = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final android.os.Messenger speechRecognizerServiceMessageReceiver = new android.os.Messenger(
            new android.os.Handler () {
                @Override
                public void handleMessage(android.os.Message msg) {
                    if (handleMessageFromSpeechRecognizerService(msg) == false) {
                        super.handleMessage(msg);
                    }
                }
            });
    /**
     * Class for interacting with the main interface of the service.
     */
    private android.content.ServiceConnection mConnection = new android.content.ServiceConnection() {
        public void onServiceConnected(android.content.ComponentName className, android.os.IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            speechRecognizerServiceMessageSender = new android.os.Messenger(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                android.os.Message msg = android.os.Message.obtain(
                        null,
                        SpeechRecognizerService.IncomingMessages.REGISTER_CLIENT.ordinal());
                msg.replyTo = speechRecognizerServiceMessageReceiver;
                speechRecognizerServiceMessageSender.send(msg);

                android.util.Log.d(LOG_TAG, "onServiceConnected() - state=" + activityState);
                if (activityState.ordinal() < ActivityState.INPUT_COMPLETE__SEARCHING_FOR_TRACKS.ordinal()) {
                    msg = android.os.Message.obtain(
                            null,
                            SpeechRecognizerService.IncomingMessages.START_RECOGNIZING.ordinal());
                    msg.replyTo = speechRecognizerServiceMessageReceiver;
                    speechRecognizerServiceMessageSender.send(msg);
                }
            } catch (android.os.RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

            mBound = true;
        }

        public void onServiceDisconnected(android.content.ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            speechRecognizerServiceMessageSender = null;
            mBound = false;
        }
    };



    //////////////////////////////////////////////////////////////////////
    //
    // overridden member methods
    //
    //////////////////////////////////////////////////////////////////////


    //////////////////////////////////////////////////////////////////////
    // methods overridden from class android.app.Activity
    //////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.activityState = ActivityState.WAITING_FOR_INPUT;
        this.mainViewGroup = (android.view.ViewGroup) (findViewById(R.id.mainViewGroup));
        this.whatsHappeningTextView = (android.widget.TextView) (findViewById(R.id.whatsHappeningTextView));
        this.hintTextView = (android.widget.TextView) (findViewById(R.id.hintTextView));
        this.mainTextView = (android.widget.TextView) (findViewById(R.id.mainTextView));
//        SpotifyAuthentication.openAuthWindow(CLIENT_ID, "token", REDIRECT_URI,
//                new String[]{"user-read-private", "streaming"}, null, this);

        android.view.View.OnClickListener mainOnClickListener = createMainOnClickListener();
        this.mainViewGroup.setOnClickListener(mainOnClickListener);
        this.mainTextView.setOnClickListener(mainOnClickListener);

        // Create global configuration and initialize ImageLoader with this config
        com.nostra13.universalimageloader.core.ImageLoaderConfiguration config
                = new com.nostra13.universalimageloader.core.ImageLoaderConfiguration.Builder(this)
                //.memoryCacheExtraOptions(480, 800) // default = device screen dimensions
                //.diskCacheExtraOptions(480, 800, null)
                //.taskExecutor(...)
                //.taskExecutorForCachedImages(...)
                //.threadPoolSize(3) // default
                //.threadPriority(Thread.NORM_PRIORITY - 2) // default
                //.tasksProcessingOrder(QueueProcessingType.FIFO) // default
                //.denyCacheImageMultipleSizesInMemory()
                //.memoryCache(new LruMemoryCache(2 * 1024 * 1024))
                //.memoryCacheSize(2 * 1024 * 1024)
                //.memoryCacheSizePercentage(13) // default
                //.diskCache(new UnlimitedDiscCache(cacheDir)) // default
                //.diskCacheSize(50 * 1024 * 1024)
                //.diskCacheFileCount(100)
                //.diskCacheFileNameGenerator(new HashCodeFileNameGenerator()) // default
                //.imageDownloader(new BaseImageDownloader(context)) // default
                //.imageDecoder(new BaseImageDecoder()) // default
                //.defaultDisplayImageOptions(DisplayImageOptions.createSimple()) // default
                .writeDebugLogs()
                .build();
        com.nostra13.universalimageloader.core.ImageLoader.getInstance().init(config);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent serviceIntent = new Intent(this, SpeechRecognizerService.class);
        startService(serviceIntent);
        // Bind to the service
        bindService(serviceIntent, mConnection,
                android.content.Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        if (!isChangingConfigurations ())
        {
            stopService(new Intent(this, SpeechRecognizerService.class));
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        // Restore state members from saved instance
        this.setUIState( (savedInstanceState==null) ? ActivityState.WAITING_FOR_INPUT
                                                    : ActivityState.values()[savedInstanceState.getInt("activityState")],
                         savedInstanceState.getString("mainTextViewText") );
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the state of the activity
        savedInstanceState.putInt("activityState", activityState.ordinal());
        savedInstanceState.putString("mainTextViewText", mainTextView.getText().toString());

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);

        Uri uri = intent.getData();
        if (uri != null) {
            AuthenticationResponse response = SpotifyAuthentication.parseOauthResponse(uri);
            com.spotify.sdk.android.playback.Config playerConfig = new com.spotify.sdk.android.playback.Config(this, response.getAccessToken(), CLIENT_ID);
            Spotify spotify = new Spotify();
            mPlayer = spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                @Override
                public void onInitialized() {
                    mPlayer.addConnectionStateCallback(MainActivity.this);
                    mPlayer.addPlayerNotificationCallback(MainActivity.this);
                    mPlayer.play("spotify:track:" + trackId);

                }

                @Override
                public void onError(Throwable throwable) {
                    Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                }
            });
        }

    }

    @Override
    protected void onDestroy() {
        // VERY IMPORTANT! This must always be called or else you will leak resources
        Spotify.destroyPlayer(this);

        super.onDestroy();
    }

    /**
     * Initialize the contents of the Activity's standard options menu.  You
     * should place your menu items in to <var>menu</var>.
     * <p/>
     * <p>This is only called once, the first time the options menu is
     * displayed.  To update the menu every time it is displayed, see
     * {@link #onPrepareOptionsMenu}.
     * <p/>
     * <p>The default implementation populates the menu with standard system
     * menu items.  These are placed in the {@link android.view.Menu#CATEGORY_SYSTEM} group so that
     * they will be correctly ordered with application-defined menu items.
     * Deriving classes should always call through to the base implementation.
     * <p/>
     * <p>You can safely hold on to <var>menu</var> (and any items created
     * from it), making modifications to it as desired, until the next
     * time onCreateOptionsMenu() is called.
     * <p/>
     * <p>When you add items to the menu, you can implement the Activity's
     * {@link #onOptionsItemSelected} method to handle them there.
     *
     * @param menu The options menu in which you place your items.
     * @return You must return true for the menu to be displayed;
     * if you return false it will not be shown.
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * This hook is called whenever an item in your options menu is selected.
     * The default implementation simply returns false to have the normal
     * processing happen (calling the item's Runnable or sending a message to
     * its Handler as appropriate).  You can use this method for any items
     * for which you would like to do processing without those other
     * facilities.
     * <p/>
     * <p>Derived classes should call through to the base class for it to
     * perform the default menu handling.</p>
     *
     * @param item The menu item that was selected.
     * @return boolean Return false to allow normal menu processing to
     * proceed, true to consume it here.
     * @see #onCreateOptionsMenu
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    //////////////////////////////////////////////////////////////////////
    // Methods implementing the interface
    //    com.spotify.sdk.android.playback.ConnectionStateCallback
    //////////////////////////////////////////////////////////////////////

    @Override
    public void onLoggedIn() {
        android.util.Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        android.util.Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        android.util.Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        android.util.Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onNewCredentials(String s) {
        android.util.Log.d("MainActivity", "User credentials blob received");
    }

    @Override
    public void onConnectionMessage(String message) {
        android.util.Log.d("MainActivity", "Received connection message: " + message);
    }

    //////////////////////////////////////////////////////////////////////
    // Methods implementing the interface
    //    com.spotify.sdk.android.playback.PlayerNotificationCallback
    //////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("MainActivity", "Playback event received: " + eventType.name());
        switch (eventType) {
            // Handle event type as necessary
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d("MainActivity", "Playback error received: " + errorType.name());
        switch (errorType) {
            // Handle error type as necessary
            default:
                break;
        }
    }



    //////////////////////////////////////////////////////////////////////
    // methods unique to this class (non-overridden)
    //////////////////////////////////////////////////////////////////////

    private android.view.View.OnClickListener createMainOnClickListener ()
    {
        return new android.view.View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MainActivity.this.activityState == ActivityState.INACTIVE) {
                    setUIState(ActivityState.WAITING_FOR_INPUT, "");

                    try {
                        android.os.Message msg = android.os.Message.obtain(
                                null,
                                SpeechRecognizerService.IncomingMessages.START_RECOGNIZING.ordinal());
                        msg.replyTo = speechRecognizerServiceMessageReceiver;
                        speechRecognizerServiceMessageSender.send(msg);
                    } catch (android.os.RemoteException remoteException) {
                        setUIState(ActivityState.INACTIVE, "Error initializing speech recognizer");
                    }
                }
            }
        };
    }

    private void setUIState (ActivityState newActivityState, String mainTextViewText) {
        ActivityState previousState = this.activityState;
        this.activityState = newActivityState;

        if (this.activityState == ActivityState.WAITING_FOR_INPUT) {
            this.whatsHappeningTextView.setText("");
            this.mainTextView.setText(R.string.what_do_you_want);
            this.hintTextView.setText(R.string.what_do_you_want_hint);
            this.mainViewGroup.setBackgroundColor(getResources().getColor(R.color.mainViewGroupPassiveBackgroundColor));
        }
        else if (this.activityState == ActivityState.PARTIAL_INPUT_RECIEVED) {
            this.whatsHappeningTextView.setText(R.string.whats_happening_listening);
            this.mainViewGroup.setBackgroundColor(getResources().getColor(R.color.mainViewGroupActiveBackgroundColor));
            this.mainTextView.setText(mainTextViewText);
        }
        else if (this.activityState == ActivityState.INPUT_COMPLETE__SEARCHING_FOR_TRACKS) {
            this.hintTextView.setText("");
            this.whatsHappeningTextView.setText(R.string.whats_happening_searching);
            this.mainViewGroup.setBackgroundColor(getResources().getColor(R.color.mainViewGroupSearchingBackgroundColor));
            this.mainTextView.setText(mainTextViewText);
        }
        else if (this.activityState == ActivityState.INPUT_COMPLETE__SEARCH_COMPLETE) {
            this.hintTextView.setText("");
            this.whatsHappeningTextView.setText("");
            this.mainViewGroup.setBackgroundColor(getResources().getColor(R.color.mainViewGroupRecognizedBackgroundColor));
            this.mainTextView.setText(mainTextViewText);
        }
        else if (this.activityState == ActivityState.INACTIVE) {
            this.hintTextView.setText("");
            this.whatsHappeningTextView.setText("");
            this.mainViewGroup.setBackgroundColor(getResources().getColor(R.color.mainViewGroupInactiveBackgroundColor));
            this.mainTextView.setText(mainTextViewText);
        }
    }

    private boolean handleMessageFromSpeechRecognizerService (android.os.Message msg) {
        boolean messageHandled = true;

        if (msg.what == SpeechRecognizerService.OutgoingMessages.UNRECOGNIZED_REQUEST.ordinal()) {
            android.widget.Toast.makeText(MainActivity.this, "Got UNRECOGNIZED_REQUEST message from service: ", android.widget.Toast.LENGTH_SHORT);
        }
        else if (msg.what == SpeechRecognizerService.OutgoingMessages.BEGINNING_OF_SPEECH.ordinal()) {
            //android.widget.Toast.makeText(MainActivity.this, "Got PARTIAL_RESULTS message from service: ", android.widget.Toast.LENGTH_SHORT);
            setUIState(ActivityState.PARTIAL_INPUT_RECIEVED, "");
        }
        else if (msg.what == SpeechRecognizerService.OutgoingMessages.PARTIAL_RESULTS.ordinal()) {
            java.util.ArrayList<String> data = (java.util.ArrayList<String>)(msg.getData().getSerializable("data"));
            android.widget.Toast.makeText(MainActivity.this, "Got PARTIAL_RESULTS message from service: ", android.widget.Toast.LENGTH_SHORT);
            setUIState(ActivityState.PARTIAL_INPUT_RECIEVED, data.get(0));
        }
        else if (msg.what == SpeechRecognizerService.OutgoingMessages.RECOGNIZED_TEXT.ordinal()) {
            //android.widget.Toast.makeText(MainActivity.this, "Got RECOGNIZED_TEXT message from service: ", android.widget.Toast.LENGTH_SHORT);
            android.util.Log.d(LOG_TAG, "handleMessageFromSpeechRecognizerService() - got results:" + msg.getData().getSerializable("data"));
            java.util.ArrayList<String> data = (java.util.ArrayList<String>)(msg.getData().getSerializable("data"));
            String mostLikelySpokenText = data.get(0);
//            setUIState(ActivityState.INPUT_COMPLETE__SEARCHING_FOR_TRACKS, mostLikelySpokenText);

//            searchTracksAsync(mostLikelySpokenText);
            Intent searchResultsActivityIntent = new Intent(MainActivity.this, TrackListActivity.class);
            searchResultsActivityIntent.putExtra("queryText", mostLikelySpokenText);
//            searchResultsActivityIntent.putExtra("queryResults", result);

            startActivity(searchResultsActivityIntent);

            setUIState(ActivityState.WAITING_FOR_INPUT, "");
        }
        else if (msg.what == SpeechRecognizerService.OutgoingMessages.ERROR.ordinal()) {
            //android.widget.Toast.makeText(MainActivity.this, "Got RECOGNIZED_TEXT message from service: ", android.widget.Toast.LENGTH_SHORT);
            android.util.Log.d(LOG_TAG, "handleMessageFromSpeechRecognizerService() - Error");
            String errorMessage = (String)(msg.getData().getSerializable("data"));
            setUIState(ActivityState.INACTIVE, errorMessage);
        }
        else {
            messageHandled = false;
        }

        return messageHandled;
    }

    private void searchTracksAsync (final String queryText) {
        android.os.AsyncTask asyncTask = new android.os.AsyncTask<Object, Object, android.database.Cursor>() { //<params, progress, result>
            /**
             * Override this method to perform a computation on a background thread. The
             * specified parameters are the parameters passed to {@link #execute}
             * by the caller of this task.
             *
             * This method can call {@link #publishProgress} to publish updates
             * on the UI thread.
             *
             * @param params The parameters of the task.
             *
             * @return A result, defined by the subclass of this task.
             *
             * @see #onPreExecute()
             * @see #onPostExecute
             * @see #publishProgress
             */
            public android.database.Cursor doInBackground (Object... params) {
                android.database.Cursor result = null;
                final String queryTextParam = (String)(params[0]);

                String[] requestedColumns = new String[]{"id", "title", "artistNames", "previewUrl", "iconUrl"};
                String[] selectionParameters = {queryTextParam};
                String sortOrder = "";
//                return SpotifyWebApiClient.searchTracks(queryTextParam);
                try {
                   result = getContentResolver().query(
                            com.josephbanta.avjukebox.data.SpotifyContract.TrackEntry.CONTENT_URI,   // The content URI of the words table
                            requestedColumns,            // The columns to return for each row
                            "query=?",                    // Selection criteria
                            selectionParameters,          // Selection criteria parameters
                            sortOrder );                  // The sort order for the returned rows
                } catch (Exception e) {
                    android.util.Log.e(LOG_TAG, "Exeption querying provider: " + e);
                    e.printStackTrace();
                }

                return result;
            }

            /**
             * Runs on the UI thread after {@link #publishProgress} is invoked.
             * The specified values are the values passed to {@link #publishProgress}.
             *
             * @param values The values indicating progress.
             *
             * @see #publishProgress
             * @see #doInBackground
             */
            protected void onProgressUpdate(Object... values) {
            }

            /**
             * <p>Runs on the UI thread after {@link #doInBackground}. The
             * specified result is the value returned by {@link #doInBackground}.</p>
             *
             * <p>This method won't be invoked if the task was cancelled.</p>
             *
             * @param result The result of the operation computed by {@link #doInBackground}.
             *
             * @see #onPreExecute
             * @see #doInBackground
             * @see #onCancelled(Object)
             */
            protected void onPostExecute(android.database.Cursor result) {
                int numCursorRecords = result.getCount();
                if (numCursorRecords == 0) {
                    android.util.Log.d(LOG_TAG, "searchTracksAsync() - spotify search yielded no results.");
                    setUIState(ActivityState.INPUT_COMPLETE__SEARCH_COMPLETE, getString(R.string.no_tracks_found));
                }
                else {
                    String searchString = "Search yielded " + numCursorRecords + " tracks\n";

                    setUIState(ActivityState.INPUT_COMPLETE__SEARCH_COMPLETE, searchString);

                    Intent searchResultsActivityIntent = new Intent(MainActivity.this, TrackListActivity.class);
                    //searchResultsActivityIntent.putExtra("queryText", queryText);
                    //earchResultsActivityIntent.putExtra("queryResults", result);

                    startActivity(searchResultsActivityIntent);

                    setUIState(ActivityState.WAITING_FOR_INPUT, "");
                    //SpotifyAuthentication.openAuthWindow(CLIENT_ID, "token", REDIRECT_URI,
                    //            new String[]{"user-read-private", "streaming"}, null, MainActivity.this);
                }
/*
                try {
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(result);
                    org.json.JSONObject tracks = jsonResponse.getJSONObject("tracks");
                    org.json.JSONArray trackArray = tracks.getJSONArray("items");

                    if (trackArray.length() <= 0) {
                        android.util.Log.d(LOG_TAG, "searchTracksAsync() - spotify search yielded no results.");
                        setUIState(ActivityState.INPUT_COMPLETE__SEARCH_COMPLETE, getString(R.string.no_tracks_found));
                    }
                    else {
                        String searchString = "Search yielded " + trackArray.length() + " tracks\n";

                        setUIState(ActivityState.INPUT_COMPLETE__SEARCH_COMPLETE, searchString);

                        Intent searchResultsActivityIntent = new Intent(MainActivity.this, TrackListActivity.class);
                        searchResultsActivityIntent.putExtra("queryText", queryText);
                        searchResultsActivityIntent.putExtra("queryResults", result);
                        startActivity(searchResultsActivityIntent);

                        setUIState(ActivityState.WAITING_FOR_INPUT, "");
                        //SpotifyAuthentication.openAuthWindow(CLIENT_ID, "token", REDIRECT_URI,
                        //            new String[]{"user-read-private", "streaming"}, null, MainActivity.this);
                    }
                } catch (org.json.JSONException jsonException) {

                }
*/
            }
        };
        asyncTask.execute(queryText);

    }

}
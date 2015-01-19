package com.josephbanta.avjukebox;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.josephbanta.avjukebox.service.MediaPlayerService;


public class TrackListActivity extends android.support.v7.app.ActionBarActivity {
    // constants
    public final String LOG_TAG = TrackListActivity.class.getSimpleName();

    // member variables
    private java.util.Timer inactivityTimer = null;
    private java.lang.Runnable inactivityCallback = null;
    private int inactivityDelay = 0;

    //////////////////////////////////////////////////////////////////////
    // methods overridden from class
    //                     android.app.Activity
    //////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_search_results);

        if (savedInstanceState == null) {
            TrackListFragment searchResultsFragment = new TrackListFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, searchResultsFragment)
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Bundle extras = getIntent().getExtras();
        if ( (extras != null)
          && extras.containsKey("queryText") )
        {
            String queryText = extras.getString("queryText");
            setTitle(getString(R.string.title_activity_search_results) + ": \"" + queryText+ "\"");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_search_results, menu);

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

    /**
     * Called whenever a key, touch, or trackball event is dispatched to the
     * activity.  Implement this method if you wish to know that the user has
     * interacted with the device in some way while your activity is running.
     * This callback and onUserLeaveHint are intended to help
     * activities manage status bar notifications intelligently; specifically,
     * for helping activities determine the proper time to cancel a notfication.
     * <p/>
     * <p>All calls to your activity's onUserLeaveHint callback will
     * be accompanied by calls to {@link #onUserInteraction}.  This
     * ensures that your activity will be told of relevant user activity such
     * as pulling down the notification pane and touching an item there.
     * <p/>
     * <p>Note that this callback will be invoked for the touch down action
     * that begins a touch gesture, but may not be invoked for the touch-moved
     * and touch-up actions that follow.
     *
     * see onUserLeaveHint()
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        resetInactivityTimer();
    }

    /**
     * Called as part of the activity lifecycle when an activity is about to go
     * into the background as the result of user choice.  For example, when the
     * user presses the Home key, {@link #onUserLeaveHint} will be called, but
     * when an incoming phone call causes the in-call Activity to be automatically
     * brought to the foreground, {@link #onUserLeaveHint} will not be called on
     * the activity being interrupted.  In cases when it is invoked, this method
     * is called right before the activity's {@link #onPause} callback.
     * <p/>
     * <p>This callback and {@link #onUserInteraction} are intended to help
     * activities manage status bar notifications intelligently; specifically,
     * for helping activities determine the proper time to cancel a notfication.
     *
     * @see #onUserInteraction()
     */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        // ensure that the inactivity timer is not fired after the activity is closed (or backgrounded)
        cancelInactivityCallback();
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        android.os.Bundle extras = intent.getExtras();
        android.util.Log.d(LOG_TAG, "onNewIntent(" + (((extras != null) && (extras.containsKey("queryText"))) ? ("queryText=" + extras.getString("queryText")) : "") + ")");
    }

    //////////////////////////////////////////////////////////////////////
    // methods unique to this class (non-overridden)
    //////////////////////////////////////////////////////////////////////
    private void resetInactivityTimer ()
    {
        if (this.inactivityCallback != null) {
            if (this.inactivityTimer != null) {
                this.inactivityTimer.cancel();
            }

            this.inactivityTimer = new java.util.Timer();
            this.inactivityTimer.schedule(
                new java.util.TimerTask() {
                    /**
                     * The task to run should be specified in the implementation of the {@code run()}
                     * method.
                     */
                    @Override
                    public void run() {
                        inactivityCallback.run();
                    }
                }, inactivityDelay );
        }
    }

    public boolean isInactivityCallbackScheduled ()
    {
        return ( (this.inactivityTimer != null)
              && (this.inactivityCallback != null) );
    }

    public void scheduleInactivityCallback ( final java.lang.Runnable callback, int millisecondsDelay ) {
        cancelInactivityCallback();

        this.inactivityCallback = callback;
        this.inactivityDelay = java.lang.Math.max(0, millisecondsDelay);

        resetInactivityTimer();
    }

    public void cancelInactivityCallback () {
        if (this.inactivityTimer != null) {
            this.inactivityTimer.cancel();
            this.inactivityTimer = null;
        }
        this.inactivityCallback = null;
        this.inactivityDelay = 0;
    }

    //////////////////////////////////////////////////////////////////////
    // inner classes
    //////////////////////////////////////////////////////////////////////

    /**
     * A fragment containing a list view that displays the results of a spotify search.
     */
    public static class TrackListFragment    extends android.support.v4.app.Fragment
                                          implements android.support.v4.app.LoaderManager.LoaderCallbacks<android.database.Cursor>
    {

        // constants
        public final String LOG_TAG = TrackListFragment.class.getSimpleName();

        // Identifies a particular Loader being used in this component
        private static final int CURSOR_LOADER = 0;

        // member variables
        private android.widget.ListView      listView;
        private android.widget.CursorAdapter cursorAdapter;
        private android.app.ProgressDialog   progressDialog = null;

        private String                       queryText = null;
        private boolean                      boundToService = false;
        private MediaPlayerServiceConnection mConnection = new MediaPlayerServiceConnection();
        private android.os.Messenger         mediaPlayerServiceMessageSender = null;
        private android.os.Messenger         mediaPlayerServiceMessageReceiver = new android.os.Messenger(new MediaPlayerServiceMessageReceiver());

        private int[]                        listViewPosition = null;
        private boolean                      resumedFromSavedInstance = false;
        private boolean                      autoPlayFirstTriggerred = false;

        private java.util.HashMap<String, java.util.HashMap> mediaPlayerStreamState = null;


        //////////////////////////////////////////////////////////////////////
        // constructors
        //////////////////////////////////////////////////////////////////////

        public TrackListFragment() {
        }

        //////////////////////////////////////////////////////////////////////
        // methods overridden from class
        //                    android.app.Fragment
        //////////////////////////////////////////////////////////////////////

        @Override
        public View onCreateView( LayoutInflater inflater,
                                  ViewGroup container,
                                  Bundle savedInstanceState ) {
            View rootView = inflater.inflate(R.layout.fragment_search_results, container, false);

            //cursorAdapter = new android.widget.SimpleCursorAdapter(
            //            // Context context -- The context where the ListView associated with this SimpleListItemFactory is running
            //            getActivity(),
            //            // int layout -- resource identifier of a layout file that defines the views for this list item. The layout file should include at least those named views defined in "to"
            //            R.layout.search_result_list_item,
            //            // Cursor c -- The database cursor.  Can be null if the cursor is not available yet.
            //            null,
            //            // String[] from -- A list of column names representing the data to bind to the UI.  Can be null if the cursor is not available yet.
            //            new String[]{"title", "artistNames"},
            //            // int[] to -- The views that should display column in the "from" parameter. These should all be TextViews. The first N views in this list are given the values of the first N columns in the from parameter.  Can be null if the cursor is not available yet.
            //            new int[]{R.id.list_item_title_textview, R.id.list_item_artists_textview},
            //            // int flags -- Flags used to determine the behavior of the adapter, as per CursorAdapter.CursorAdapter(Context, Cursor, int).
            //            android.widget.SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
            cursorAdapter = new TrackListCursorAdapter(
                    getActivity(), // android.content.Context context -- The context where the ListView associated with this is running
                    null,          // android.database.Cursor cursor  -- The database cursor.  Can be null if the cursor is not available yet.
                    0 );           // int flags                       -- Flags used to determine the behavior of the adapter, as per CursorAdapter.CursorAdapter(Context, Cursor, int).

            // Get a reference to the ListView, and attach this adapter to it.
            this.listView = (android.widget.ListView)rootView.findViewById(R.id.listview_search_results);
            this.listView.setAdapter(cursorAdapter);
            this.listView.setOnItemClickListener(createListViewClickListener());

            return rootView;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            android.os.Bundle activityExtras = getActivity().getIntent().getExtras();
            if ( (activityExtras != null)
              && activityExtras.containsKey("queryText") )
            {
                this.queryText = activityExtras.getString("queryText");
            }
            getLoaderManager().initLoader(CURSOR_LOADER, activityExtras, this);

            super.onActivityCreated(savedInstanceState);

            // restore the path of the queued audio, if any, and the position of the currently selected item in the list
            if (savedInstanceState != null) {
                listViewPosition = savedInstanceState.getIntArray("listViewPosition");
                resumedFromSavedInstance = true;
            }
            else {
                resumedFromSavedInstance = false;
            }

            // create a listener that will notify the media player service any time the shared preference are updated
            android.content.SharedPreferences sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
            sharedPreferences.registerOnSharedPreferenceChangeListener (
                    new android.content.SharedPreferences.OnSharedPreferenceChangeListener() {
                        public void onSharedPreferenceChanged(android.content.SharedPreferences prefsParam, String key) {
                            if ( key.equals(getString(R.string.pref_autoplay_key))
                                    || key.equals(getString(R.string.pref_autoplay_next_key))
                                    || key.equals(getString(R.string.pref_fade_in_out_key))
                                    || key.equals(getString(R.string.pref_one_clip_at_a_time_key)) )
                            {
                                boolean newValue = prefsParam.getBoolean(key, true);
                                android.os.Message message = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.PREFERENCE_UPDATE);
                                android.os.Bundle messageData = message.getData();
                                messageData.putBoolean(key, newValue);
                                try {
                                    sendMessageToMediaPlayerService(message);
                                } catch (Exception remoteException) {
                                    android.util.Log.d(LOG_TAG, "Error communicating preference update to media player service: " + remoteException);
                                }
                            }
                        }
                    } );
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            // save index and top position
            int index = listView.getFirstVisiblePosition();
            View v = listView.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();
            outState.putIntArray("listViewPosition", new int[]{index, top});
        }

        @Override
        public void onStart() {
            super.onStart();

            android.content.Intent serviceIntent = new android.content.Intent(getActivity(), MediaPlayerService.class);
            android.app.Activity activity = getActivity();
            activity.startService(serviceIntent);
            // Bind to the service
            activity.bindService(serviceIntent, mConnection,
                    android.content.Context.BIND_AUTO_CREATE);
        }

        @Override
        public void onStop() {
            super.onStop();

            // Unbind from the service
            android.app.Activity activity = getActivity();
            if (boundToService) {
                try {
                    android.os.Message outgoingMessage = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.DEREGISTER_CLIENT);
                    outgoingMessage.replyTo = mediaPlayerServiceMessageReceiver;
                    mediaPlayerServiceMessageSender.send(outgoingMessage);

                } catch (android.os.RemoteException e) {}

                activity.unbindService(mConnection);
                boundToService = false;
            }

            if (!activity.isChangingConfigurations())
            {
                //activity.stopService(new android.content.Intent(activity, MediaPlayerService.class));
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            if (listViewPosition != null) {
                listView.setSelectionFromTop(listViewPosition[0], listViewPosition[1]);
                listViewPosition=null;
            }
            try {
                sendMessageToMediaPlayerService(MediaPlayerService.MessagesToService.QUERY_STATUS);
            } catch (Exception exception) {
                android.util.Log.d(LOG_TAG, "Error querying service for stream status in onResume -- " + exception);
            }
        }




        //////////////////////////////////////////////////////////////////////
        // methods implementing the interface
        //   android.support.v4.app.LoaderManager.LoaderCallbacks<android.database.Cursor>
        //////////////////////////////////////////////////////////////////////

        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            Loader<Cursor> result = null;

            // create and return a CursorLoader that will take care of
            // creating a Cursor for the data being displayed.
            String[] requestedColumns = new String[]{"id", "title", "artistNames", "previewUrl", "iconUrl"};
            if (bundle != null) {
                this.queryText = bundle.getString("queryText");

                this.progressDialog = android.app.ProgressDialog.show(
                        getActivity(),     //Context context,
                        "",                // CharSequence title,
                        "Searching for '" + this.queryText + "'...", // CharSequence message
                        true );            // boolean indeterminate

                result =  new android.support.v4.content.CursorLoader(
                        getActivity(),           // android.content.Context context
                        com.josephbanta.avjukebox.data.SpotifyContract.TrackEntry.CONTENT_URI, // android.net.Uri uri
                        requestedColumns,        // java.lang.String[] projection
                        "query=?",               // java.lang.String selection
                        new String[]{this.queryText}, // java.lang.String[] selectionArgs
                        null );                  // java.lang.String sortOrder
            }

            return result;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            cursorAdapter.swapCursor(cursor);

            if (cursor.getCount() == 0) {
                final Activity encapsulatingActivity = getActivity();
                android.app.AlertDialog.Builder alertDialogBuilder
                        = (new android.app.AlertDialog.Builder(getActivity()))
                            .setTitle("No matching tracks")
                            .setMessage("Your query '" + this.queryText + "' did not match any tracks.")
                            .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    encapsulatingActivity.finish();
                                }
                            });
                android.app.AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        encapsulatingActivity.finish();
                    }
                });
                alertDialog.show();

                //Intent i = new Intent();
                //i.setAction(Intent.ACTION_MAIN);
                //i.addCategory(Intent.CATEGORY_HOME);
                //i.putExtra("no results", true);
                //encapsulatingActivity.startActivity(i);

            }
            else {
                android.content.SharedPreferences defaultSharedPrefs = android.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                if ((cursor.getCount() > 0)
                        && (defaultSharedPrefs.getBoolean(getString(R.string.pref_autoplay_key), true))) {
                    //queueClipForPlayback(0);
                    autoPlayFirstTriggerred = true;
                }

                // resync the ui with the actual status of the streams in the player service; this is done by querying the service
                // for the status of all of its playing streams.  The reply will be handled asynchronously in
                // handleMessageFromMediaPlayerService()
                try {
                    sendMessageToMediaPlayerService(MediaPlayerService.MessagesToService.QUERY_STATUS);
                } catch (android.os.RemoteException remoteException) {
                    android.util.Log.d(LOG_TAG, "Exception querying player service for status: " + remoteException);
                } catch (java.io.NotActiveException notActiveException) {
                    android.util.Log.d(LOG_TAG, "Exception querying player service for status: " + notActiveException);
                }
            }

            if (this.progressDialog != null) {
                this.progressDialog.dismiss();
                this.progressDialog = null;
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
            cursorAdapter.swapCursor(null);
        }


        //////////////////////////////////////////////////////////////////////
        // methods unique to this class (non-overrides)
        //////////////////////////////////////////////////////////////////////

        private Integer getIdPosition (String clipId) {
            Integer position = null;

            android.database.Cursor cursor = cursorAdapter.getCursor();
            if ((cursor != null) && (cursor.getCount() > 0)) {
                for (cursor.moveToFirst(); true; cursor.moveToNext()) {
                    String thisRecordPreviewUrl = cursor.getString(cursor.getColumnIndex("id"));
                    if (thisRecordPreviewUrl.equals(clipId)) {
                        position = cursor.getPosition();
                        break;
                    }

                    if (cursor.isLast()) { break; }
                }
            }

            return position;
        }

        private android.os.Message createMessageToMediaPlayerService (MediaPlayerService.MessagesToService messageType)
        {
            android.os.Message msg = android.os.Message.obtain(
                    null,
                    messageType.ordinal());
            msg.replyTo = mediaPlayerServiceMessageReceiver;

            return msg;
        }

        private void sendMessageToMediaPlayerService (MediaPlayerService.MessagesToService messageType)
                throws android.os.RemoteException,
                java.io.NotActiveException
        {
            sendMessageToMediaPlayerService(createMessageToMediaPlayerService(messageType));
        }

        private void sendMessageToMediaPlayerService (android.os.Message msg)
                throws android.os.RemoteException,
                java.io.NotActiveException
        {
            if (mediaPlayerServiceMessageSender == null) {
                throw new java.io.NotActiveException("Service not connected");
            }
            else {
                mediaPlayerServiceMessageSender.send(msg);
            }
        }

        private View getViewByPosition(int pos) {
            android.view.View result = null;
            final int firstListItemPosition = listView.getFirstVisiblePosition();
            final int lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1;

            if ((pos >= firstListItemPosition) && (pos <= lastListItemPosition) ) {
                final int childIndex = pos - firstListItemPosition;
                result = listView.getChildAt(childIndex);
            }

            return result;
        }

        private void handleMessageFromMediaPlayerService(android.os.Message msg)
        {
            if (msg.what == MediaPlayerService.MessagesToClient.UNRECOGNIZED_REQUEST.ordinal()) {
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(UNRECOGNIZED_REQUEST)");
            }

            else if (msg.what == MediaPlayerService.MessagesToClient.PLAYER_STATUS.ordinal()) {
                // this message is automatically sent in response to the client being registered
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(PLAYER_STATUS)");
                android.os.Bundle messageData = msg.getData();
                if (messageData.containsKey("streamState")) {
                    java.util.HashMap<String, java.util.HashMap> oldStreamState = this.mediaPlayerStreamState;
                    this.mediaPlayerStreamState = (java.util.HashMap<String, java.util.HashMap>) messageData.getSerializable("streamState");

                    if (oldStreamState != null) {
                        // identify any streams that were listed in the previous state map, but not in the new one
                        for (String id : oldStreamState.keySet()) {
                            if (!(this.mediaPlayerStreamState.containsKey(id))) {
                                updatePlayerState(id, MediaPlayerService.StreamState.FINISHED);
                            }
                        }
                    }

                    boolean anyListedStreamPlayingOrPaused = false;
                    for (String id : this.mediaPlayerStreamState.keySet()) {
                        java.util.HashMap thisStreamInfo = this.mediaPlayerStreamState.get(id);
                        MediaPlayerService.StreamState streamState = (MediaPlayerService.StreamState)(thisStreamInfo.get("state"));
                        updatePlayerState(id, streamState);

                        Integer pathListPosition = getIdPosition(id);
                        if ( (pathListPosition != null)
                          && ( (streamState == MediaPlayerService.StreamState.QUEUED)
                            || (streamState == MediaPlayerService.StreamState.FADING_IN)
                            || (streamState == MediaPlayerService.StreamState.PLAYING)
                            || (streamState == MediaPlayerService.StreamState.FADING_OUT_PRIOR_TO_PAUSE)
                            || (streamState == MediaPlayerService.StreamState.FADING_OUT)
                            || (streamState == MediaPlayerService.StreamState.PAUSED) ) )
                        {
                            anyListedStreamPlayingOrPaused = true;
                        }
                    }

                    if ( (resumedFromSavedInstance == false)
                      && (autoPlayFirstTriggerred == true)
                      && (anyListedStreamPlayingOrPaused == false) )
                    {
                        // we just reloaded the listview and auto-play is enabled, but none of the listed tracks are already playing, so
                        // play the first listed track
                        queueClipForPlayback(0);
                    }
                } // if (messageData.containsKey("streamState"))
            } // if (msg.what == MediaPlayerService.MessagesToClient.PLAYER_STATUS.ordinal())

            else if (msg.what == MediaPlayerService.MessagesToClient.STREAM_STATE_CHANGED.ordinal()) {
                android.os.Bundle messageData = msg.getData();
                String id = messageData.getString("id");
                MediaPlayerService.StreamState newState = MediaPlayerService.StreamState.values()[msg.arg1];
                updatePlayerState(id, newState);
            }

            else if (msg.arg1 == MediaPlayerService.StreamState.PAUSED.ordinal()) {
            }

            else if (msg.what == MediaPlayerService.MessagesToClient.ERROR_PLAYING_STREAM.ordinal()) {
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(ERROR_PLAYING_STREAM)");
            }

            else if (msg.what == MediaPlayerService.MessagesToClient.BUFFERING_UPDATE.ordinal()) {
            } // if (msg.what == MediaPlayerService.MessagesToClient.BUFFERING_UPDATE.ordinal())

            else if (msg.what == MediaPlayerService.MessagesToClient.READY_FOR_PLAYBACK.ordinal()) {
            }

            else if (msg.what == MediaPlayerService.MessagesToClient.PLAYING_STREAM_COMPLETE.ordinal()) {
            }

            else if (msg.what == MediaPlayerService.MessagesToClient.FADE_OUT_COMPLETED.ordinal()) {
            }

        }

        private android.widget.AdapterView.OnItemClickListener createListViewClickListener() {
            return new android.widget.AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    android.util.Log.d(LOG_TAG, "item " + position + " clicked");
                    android.widget.ListView listView = (android.widget.ListView)parent;
                    android.widget.CursorAdapter cursorAdapter = (android.widget.CursorAdapter)listView.getAdapter();
                    android.database.Cursor cursor = cursorAdapter.getCursor();
                    cursor.moveToPosition(position);

                    String clipId = cursor.getString(cursor.getColumnIndex("id"));
                    java.util.HashMap playerInfo = mediaPlayerStreamState.get(clipId);
                    MediaPlayerService.StreamState streamState = null;
                    if (playerInfo != null) {
                        streamState = (MediaPlayerService.StreamState)(playerInfo.get("state"));
                    }

                    if ( (streamState == MediaPlayerService.StreamState.FADING_IN)
                      || (streamState == MediaPlayerService.StreamState.PLAYING)
                      || (streamState == MediaPlayerService.StreamState.FADING_OUT_PRIOR_TO_PAUSE)
                      || (streamState == MediaPlayerService.StreamState.FADING_OUT) )
                    {
                        android.os.Message message = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.PAUSE_STREAM);
                        android.os.Bundle bundle = message.getData();
                        bundle.putString("id", clipId);
                        try {
                            sendMessageToMediaPlayerService(message);
                        } catch (Exception e) {
                            android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                        }

                    }
                    else {
                        //boolean clipIsPaused = false;
/*
                        for (int i=0; i<pausedAudioPath.size(); i++) {
                            String thisPausedPath = pausedAudioPath.get(i);
                            if (thisPausedPath.equals(previewUrl)) {
                                clipIsPaused = true;
                                break;
                            }
                        }
*/
                        if (streamState == MediaPlayerService.StreamState.PAUSED) {

                            android.os.Message message = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.PLAY_STREAM);
                            android.os.Bundle bundle = message.getData();
                            bundle.putString("id", cursor.getString(cursor.getColumnIndex("id")));
                            bundle.putString("title", cursor.getString(cursor.getColumnIndex("title")));
                            bundle.putString("url", cursor.getString(cursor.getColumnIndex("previewUrl")));
                            bundle.putString("trackContext", TrackListFragment.this.queryText);
                            bundle.putFloat("volume", 1.0f);
                            try {
                                sendMessageToMediaPlayerService(message);
                            } catch (Exception e) {
                                android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                            }
                        }
                        else {
                            queueClipForPlayback( position );
                        } // if (clipIsPaused == false)
                    } // if (clipIsPlaying == false)
                } // public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            }; // new android.widget.AdapterView.OnItemClickListener()
        } // private android.widget.AdapterView.OnItemClickListener createListViewClickListener()


        private void updatePlayerState (String clipId, MediaPlayerService.StreamState newState)
        {
            Integer pathListPosition = getIdPosition(clipId);
            if (pathListPosition != null) {
                android.view.View listItem = getViewByPosition(pathListPosition);
                if (listItem != null) {
                    android.widget.ProgressBar progressBar = (android.widget.ProgressBar) (listItem.findViewById(R.id.list_item_progressBar));
                    android.widget.ImageView statusIcon = (android.widget.ImageView) (listItem.findViewById(R.id.list_item_play_pause_icon));
                    if (newState == MediaPlayerService.StreamState.QUEUED) {
                        progressBar.setVisibility(android.view.View.VISIBLE);
                        statusIcon.setVisibility(android.view.View.GONE);
                    }
                    else if ( (newState == MediaPlayerService.StreamState.FADING_IN)
                           || (newState == MediaPlayerService.StreamState.PLAYING)
                           || (newState == MediaPlayerService.StreamState.FADING_OUT_PRIOR_TO_PAUSE)
                           || (newState == MediaPlayerService.StreamState.FADING_OUT) )
                    {
                        statusIcon.setImageResource(R.drawable.pause_icon);
                        progressBar.setVisibility(android.view.View.GONE);
                        statusIcon.setVisibility(android.view.View.VISIBLE);

                        if ( (newState == MediaPlayerService.StreamState.FADING_IN)
                          || (newState == MediaPlayerService.StreamState.PLAYING)
                           )
                        {
                            android.support.v4.app.FragmentActivity activity = getActivity();
                            if (activity instanceof TrackListActivity) {
                                final TrackListActivity trackListActivity = (TrackListActivity) activity;

                                if ( ! trackListActivity.isInactivityCallbackScheduled() ) {
                                    trackListActivity.scheduleInactivityCallback(
                                            new java.lang.Runnable() {
                                                @Override
                                                public void run() {
                                                    android.util.Log.i(LOG_TAG, "Inactivity threshold reached...");

                                                    android.app.Activity activity = getActivity();
                                                    if (activity != null) {
                                                        startActivity(new Intent(activity, ImageryActivity.class));
                                                    }
                                                }
                                            }, 5000);
                                }
                            }
                        }
                    }
                    else if (newState == MediaPlayerService.StreamState.PAUSED) {
                        statusIcon.setImageResource(R.drawable.play_icon);
                        progressBar.setVisibility(android.view.View.GONE);
                        statusIcon.setVisibility(android.view.View.VISIBLE);
                    }
                    else {
                        progressBar.setVisibility(android.view.View.GONE);
                        statusIcon.setVisibility(android.view.View.GONE);
                    }
                }
            }

            // update the stored state for the specified stream
            if (mediaPlayerStreamState != null) {
                java.util.HashMap<String, Object> thisStreamState = mediaPlayerStreamState.get(clipId);
                if (thisStreamState == null) { // it might not have been initialized yet
                    mediaPlayerStreamState.put(clipId, thisStreamState = new java.util.HashMap<String, Object>());
                }

                if ( (newState == MediaPlayerService.StreamState.FINISHED)
                  || (newState == MediaPlayerService.StreamState.ERROR) )
                {
                    mediaPlayerStreamState.remove(clipId);
                }
                else {
                    thisStreamState.put("state", newState);
                }
            }

            // if no players are currently playing, cancel the inactivity timer
            boolean playingStreamFound = false;
            for (java.util.HashMap streamState : mediaPlayerStreamState.values()) {
                MediaPlayerService.StreamState state = (MediaPlayerService.StreamState)(streamState.get("state"));
                if ( (state == MediaPlayerService.StreamState.FADING_IN)
                  || (state == MediaPlayerService.StreamState.PLAYING)
                  || (state == MediaPlayerService.StreamState.FADING_OUT_PRIOR_TO_PAUSE)
                  || (state == MediaPlayerService.StreamState.FADING_OUT) )
                {
                    playingStreamFound = true;
                    break;
                }
            }
            if (playingStreamFound == false) {
                android.support.v4.app.FragmentActivity activity = getActivity();
                if (activity instanceof TrackListActivity) {
                    TrackListActivity trackListActivity = (TrackListActivity) activity;
                    trackListActivity.cancelInactivityCallback();
                }
            }


        }


        private void queueClipForPlayback (int position) {
            android.widget.CursorAdapter cursorAdapter = (android.widget.CursorAdapter)listView.getAdapter();
            android.database.Cursor cursor = cursorAdapter.getCursor();
            cursor.moveToPosition(position);

            String clipId = cursor.getString(cursor.getColumnIndex("id"));
            String clipUrl = cursor.getString(cursor.getColumnIndex("previewUrl"));
            String clipTitle = cursor.getString(cursor.getColumnIndex("title"));

            java.util.HashMap thisStreamState = (mediaPlayerStreamState == null) ? null : mediaPlayerStreamState.get(clipId);
            if ( (thisStreamState != null)
              && (thisStreamState.get("state") == MediaPlayerService.StreamState.QUEUED) )
            {
                // the clip is already queued for playback ; do nothing
            }
            else {
                //android.util.Log.d(LOG_TAG, "preview url: " + clipUrl);

                android.view.View listViewItem = getViewByPosition(position);
                if (listViewItem != null) {
                    android.widget.ProgressBar progressBar = (android.widget.ProgressBar) listViewItem.findViewById(R.id.list_item_progressBar);
                    android.widget.ImageView playPauseIcon = (android.widget.ImageView) listViewItem.findViewById(R.id.list_item_play_pause_icon);
                    progressBar.setVisibility(View.VISIBLE);
                    playPauseIcon.setVisibility(View.GONE);
                }

                java.util.ArrayList<String> idsToQueue = new java.util.ArrayList<String>(),
                                            pathsToQueue = new java.util.ArrayList<String>(),
                                            titlesToQueue = new java.util.ArrayList<String>();
                while (cursor.moveToNext()) {
                    idsToQueue.add(cursor.getString(cursor.getColumnIndex("id")));
                    pathsToQueue.add(cursor.getString(cursor.getColumnIndex("previewUrl")));
                    titlesToQueue.add(cursor.getString(cursor.getColumnIndex("title")));
                }

                android.os.Message message = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.PLAY_STREAM);
                android.os.Bundle bundle = message.getData();

                bundle.putString("id", clipId);
                bundle.putString("title", clipTitle);
                bundle.putString("url", clipUrl);
                bundle.putFloat("volume", 1.0f);
                bundle.putString("trackContext", TrackListFragment.this.queryText);

                bundle.putStringArrayList("queuedIds",    idsToQueue);
                bundle.putStringArrayList("queuedTitles", titlesToQueue);
                bundle.putStringArrayList("queuedUrls",   pathsToQueue);

                try {
                    sendMessageToMediaPlayerService(message);
                } catch (Exception e) {
                    android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                }
            } // if (alreadyQueued == false)

        }

        //////////////////////////////////////////////////////////////////////
        // inner classes
        //////////////////////////////////////////////////////////////////////

        private class MediaPlayerServiceMessageReceiver extends android.os.Handler {
            @Override
            public void handleMessage(android.os.Message msg) {
                super.handleMessage(msg);
                handleMessageFromMediaPlayerService(msg);
            }
        }


        private class MediaPlayerServiceConnection implements android.content.ServiceConnection
        {
            public void onServiceConnected(android.content.ComponentName className, android.os.IBinder service) {
                // This is called when the connection with the service has been
                // established, giving us the object we can use to
                // interact with the service.  We are communicating with the
                // service using a Messenger, so here we get a client-side
                // representation of that from the raw IBinder object.
                mediaPlayerServiceMessageSender = new android.os.Messenger(service);

                // We want to monitor the service for as long as we are
                // connected to it.
                try {
                    android.os.Message msg = android.os.Message.obtain(
                            null,
                            MediaPlayerService.MessagesToService.REGISTER_CLIENT.ordinal());
                    msg.replyTo = mediaPlayerServiceMessageReceiver;

                    // include the current preferences in the registration message
                    android.content.SharedPreferences sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                    android.os.Bundle messageData = msg.getData();
                    for (int prefKey : new int[] { R.string.pref_autoplay_key,
                                                   R.string.pref_autoplay_next_key,
                                                   R.string.pref_fade_in_out_key,
                                                   R.string.pref_one_clip_at_a_time_key } )
                    {
                        String stringKey = getString(prefKey);
                        boolean prefValue = sharedPreferences.getBoolean(stringKey, true);
                        messageData.putBoolean(stringKey, prefValue);
                    }

                    mediaPlayerServiceMessageSender.send(msg);

                } catch (android.os.RemoteException e) {
                    // In this case the service has crashed before we could even
                    // do anything with it; we can count on soon being
                    // disconnected (and then reconnected if it can be restarted)
                    // so there is no need to do anything here.
                }

                boundToService = true;
            }

            public void onServiceDisconnected(android.content.ComponentName className) {
                mediaPlayerServiceMessageSender = null;
                boundToService = false;
            }
        } // private class MediaPlayerServiceConnection


        private final class ListItemData {
            public android.widget.TextView    listItemTitle;
            public android.widget.TextView    listItemArtist;
            public android.widget.ImageView   listItemIcon;
            public android.widget.ProgressBar listItemProgressBar;
            public android.widget.ImageView   listItemPlayPauseButton;
        }

        private final class TrackListCursorAdapter extends android.widget.CursorAdapter
        {
            LayoutInflater mInflater;
            com.nostra13.universalimageloader.core.ImageLoader imageLoader = com.nostra13.universalimageloader.core.ImageLoader.getInstance();

            // set up options for an asynchronous image loader
            com.nostra13.universalimageloader.core.DisplayImageOptions options = new com.nostra13.universalimageloader.core.DisplayImageOptions.Builder()
                    .showImageOnLoading(R.drawable.musical_note) // resource or drawable
                    .showImageForEmptyUri(R.drawable.musical_note) // resource or drawable
                    .showImageOnFail(R.drawable.musical_note) // resource or drawable
                    .resetViewBeforeLoading(false)  // false=default
                            //.delayBeforeLoading(1000)
                    .cacheInMemory(true) // default=false
                    .cacheOnDisk(false) // default=false
                            //.preProcessor(...)
                            //.postProcessor(...)
                            //.extraForDownloader(...)
                            //.considerExifParams(false) // default
                            //.imageScaleType(ImageScaleType.IN_SAMPLE_POWER_OF_2) // default
                            //.bitmapConfig(Bitmap.Config.ARGB_8888) // default
                            //.decodingOptions(...)
                            //.displayer(new SimpleBitmapDisplayer()) // default
                            //.handler(new Handler()) // default
                    .build();

            /**
             * Recommended constructor.
             *
             * @param context The context
             * @param cursor The cursor from which to get the data.
             * @param flags Flags used to determine the behavior of the adapter; may
             * be any combination of {@link #FLAG_AUTO_REQUERY} and
             * {@link #FLAG_REGISTER_CONTENT_OBSERVER}.
             */
            TrackListCursorAdapter(android.content.Context context, android.database.Cursor cursor, int flags) {
                super(context, cursor, flags);

                mInflater = LayoutInflater.from(context);
            }

            //////////////////////////////////////////////////////////////////////
            // methods overridden from class
            //               android.widget.CursorAdapter
            //////////////////////////////////////////////////////////////////////

            /**
             * Makes a new view to hold the data pointed to by cursor.
             * @param context Interface to application's global information
             * @param cursor The cursor from which to get the data. The cursor is already
             * moved to the correct position.
             * @param parent The parent to which the new view is attached to
             * @return the newly created view.
             */
            @Override
            public View newView(android.content.Context context, android.database.Cursor cursor, ViewGroup parent) {
                View v = mInflater.inflate(R.layout.search_result_list_item, parent, false);
                return v;
            }

            /**
             * Bind an existing view to the data pointed to by cursor
             * @param view Existing view, returned earlier by newView
             * @param context Interface to application's global information
             * @param cursor The cursor from which to get the data. The cursor is already
             * moved to the correct position.
             */
            @Override
            public void bindView(View view, android.content.Context context, android.database.Cursor cursor) {
                ListItemData listItemData = new ListItemData();
                listItemData.listItemTitle = (android.widget.TextView) view.findViewById(R.id.list_item_title_textview);
                listItemData.listItemArtist = (android.widget.TextView) view.findViewById(R.id.list_item_artists_textview);
                listItemData.listItemIcon = (android.widget.ImageView) view.findViewById(R.id.list_item_icon);
                listItemData.listItemProgressBar = (android.widget.ProgressBar) view.findViewById(R.id.list_item_progressBar);
                listItemData.listItemPlayPauseButton = (android.widget.ImageView) view.findViewById(R.id.list_item_play_pause_icon);

                listItemData.listItemTitle.setText(cursor.getString(cursor.getColumnIndex("title")));
                listItemData.listItemArtist.setText(cursor.getString(cursor.getColumnIndex("artistNames")));

                imageLoader.displayImage(cursor.getString(cursor.getColumnIndex("iconUrl")),
                        listItemData.listItemIcon, options);
                //if ((cursor.getPosition() % 2) == 1) {
                //    view.setBackgroundColor(android.graphics.Color.DKGRAY);
                //} else {
                //    view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                //}

                view.setTag(listItemData);

                boolean viewStateFound = false;
                String thisRecordId = cursor.getString(cursor.getColumnIndex("id"));

                java.util.HashMap thisStreamState = (mediaPlayerStreamState==null) ? null : mediaPlayerStreamState.get(thisRecordId);
                if (thisStreamState != null) {
                    MediaPlayerService.StreamState state = (MediaPlayerService.StreamState)(thisStreamState.get("state"));
                    if (state == MediaPlayerService.StreamState.QUEUED) {
                        listItemData.listItemProgressBar.setVisibility(View.VISIBLE);
                        listItemData.listItemPlayPauseButton.setVisibility(View.GONE);
                        viewStateFound = true;
                    }
                    else if ( (state == MediaPlayerService.StreamState.FADING_IN)
                            || (state == MediaPlayerService.StreamState.PLAYING)
                            || (state == MediaPlayerService.StreamState.FADING_OUT_PRIOR_TO_PAUSE)
                            || (state == MediaPlayerService.StreamState.FADING_OUT ) )
                    {
                        listItemData.listItemProgressBar.setVisibility(View.GONE);
                        listItemData.listItemPlayPauseButton.setImageResource(R.drawable.pause_icon);
                        listItemData.listItemPlayPauseButton.setVisibility(View.VISIBLE);
                        viewStateFound = true;
                    }
                    else if (state == MediaPlayerService.StreamState.PAUSED) {
                        listItemData.listItemProgressBar.setVisibility(View.GONE);
                        listItemData.listItemPlayPauseButton.setImageResource(R.drawable.play_icon);
                        listItemData.listItemPlayPauseButton.setVisibility(View.VISIBLE);
                        viewStateFound = true;
                   }
                }

                if (viewStateFound == false) {
                    // the item is not queued, playing, or paused.  Just hide the progress bar and the play/pause button
                    listItemData.listItemProgressBar.setVisibility(View.GONE);
                    listItemData.listItemPlayPauseButton.setVisibility(View.GONE);
                }

            } // public void bindView(View view, android.content.Context context, android.database.Cursor cursor) {
        } // private final class TrackListCursorAdapter

    }

}




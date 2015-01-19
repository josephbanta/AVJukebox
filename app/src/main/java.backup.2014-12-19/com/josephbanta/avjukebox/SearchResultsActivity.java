package com.josephbanta.avjukebox;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.josephbanta.avjukebox.service.MediaPlayerService;

import java.io.NotActiveException;


public class SearchResultsActivity extends Activity {
    // constants
    public final String LOG_TAG = SearchResultsActivity.class.getSimpleName();

    // member variables

    //////////////////////////////////////////////////////////////////////
    // methods overridden from class
    //                     android.app.Activity
    //////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);
        if (savedInstanceState == null) {
            SearchResultsFragment searchResultsFragment = new SearchResultsFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.container, searchResultsFragment)
                    .commit();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        Bundle extras = getIntent().getExtras();
        setTitle(getString(R.string.title_activity_search_results) + ": \"" + extras.getString("queryText") + "\"");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_search_results, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //////////////////////////////////////////////////////////////////////
    // methods unique to this class (non-overridden)
    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    // inner classes
    //////////////////////////////////////////////////////////////////////

    /**
     * A fragment containing a list view that displays the results of a spotify search.
     */
    public static class SearchResultsFragment extends android.app.Fragment {

        // constants
        public final String LOG_TAG = SearchResultsFragment.class.getSimpleName();

        // member variables
        private android.widget.ListView      listView;
        private boolean                      boundToService = false;
        private MediaPlayerServiceConnection mConnection = new MediaPlayerServiceConnection();
        private android.os.Messenger         mediaPlayerServiceMessageSender = null;
        private android.os.Messenger         mediaPlayerServiceMessageReceiver = new android.os.Messenger(new MediaPlayerServiceMessageReceiver());
        private String                       queuedAudioPath = null;

        //////////////////////////////////////////////////////////////////////
        // constructors
        //////////////////////////////////////////////////////////////////////

        public SearchResultsFragment() {
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

            this.listView = (android.widget.ListView)rootView.findViewById(R.id.listview_search_results);

            return rootView;
        }

        @Override
        public void onPause() {
            super.onPause();
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
                activity.unbindService(mConnection);
                boundToService = false;
            }

            if (!activity.isChangingConfigurations())
            {
                activity.stopService(new android.content.Intent(activity, MediaPlayerService.class));
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            // Bind to the service
            //android.content.Intent serviceIntent = new android.content.Intent(getActivity(), com.josephbanta.avjukebox.service.MediaPlayerService.class);
            //bindService(serviceIntent, mConnection,
            //        android.content.Context.BIND_AUTO_CREATE);

            Bundle activityExtras = getActivity().getIntent().getExtras();
            if (activityExtras != null) {
                String queryText = activityExtras.getString("queryText");
                String queryResultsJSON = activityExtras.getString("queryResults");


                // create the cursor from dynamic string data
                String[] columnNames = new String[]{"id", "title", "artistNames", "previewUrl", "iconUrl"};
                org.json.JSONArray trackArray = null;
                java.util.ArrayList<java.util.ArrayList> rows = null;
                try {
                    org.json.JSONObject jsonResponse = new org.json.JSONObject(queryResultsJSON);
                    org.json.JSONObject tracks = jsonResponse.getJSONObject("tracks");
                    trackArray = tracks.getJSONArray("items");

                    if (trackArray != null) {
                        rows = new java.util.ArrayList();
                        for (int j=0, numTracks=trackArray.length(); j < numTracks; j++) {
                            java.util.ArrayList row = new java.util.ArrayList();
                            rows.add(row);

                            org.json.JSONObject thisTrack = trackArray.getJSONObject(j);
                            //if (j == 0) {
                            String trackId = thisTrack.getString("id");
                            row.add(trackId);
                            //}

                            String trackTitle = thisTrack.getString("name");
                            row.add(trackTitle);

                            org.json.JSONArray trackArtists = thisTrack.getJSONArray("artists");
                            String trackArtistNames = "";
                            if (trackArtists.length() > 0) {
                                for (int i = 0, numArtists = trackArtists.length(); i < numArtists; i++) {
                                    org.json.JSONObject thisArtist = trackArtists.getJSONObject(i);
                                    trackArtistNames += ((i == 0) ? " by " : ", ") + thisArtist.getString("name");
                                }
                            }
                            row.add(trackArtistNames);

                            row.add(thisTrack.getString("preview_url"));

                            org.json.JSONObject album = thisTrack.getJSONObject("album");
                            String imageIcon = null;
                            if (album != null) {
                                org.json.JSONArray albumImages = album.getJSONArray("images");
                                if (albumImages.length() > 0) {
                                    int smallestWidth = 0;
                                    for (int k=0, numImages=albumImages.length(); k<numImages; k++) {
                                        org.json.JSONObject image = albumImages.getJSONObject(k);
                                        int thisWidth = image.getInt("width");
                                        if ((smallestWidth == 0) || (thisWidth < smallestWidth)) {
                                            smallestWidth = thisWidth;
                                            imageIcon = image.getString("url");
                                        }
                                    }
                                }
                            }
                            row.add(imageIcon);

                            android.util.Log.d(LOG_TAG, "searchTracksAsync() - spotify search yielded track id " + trackId + ": '" + trackTitle + "'" + trackArtistNames);
                            //searchString += "'" + trackTitle + "'" + trackArtistNames + "\n";
                        }
                    }
                } catch (org.json.JSONException jsonException) {
                    android.util.Log.d(LOG_TAG, "Error parsing JSON: " + jsonException);
                }

                if (rows != null) {
                    com.android.common.ArrayListCursor arrayListCursor = new com.android.common.ArrayListCursor(columnNames, rows);
                    //android.widget.SimpleCursorAdapter adapter = new android.widget.SimpleCursorAdapter(
                    //            getActivity(),
                    //            R.layout.search_result_list_item,
                    //            arrayListCursor,
                    //            new String[]{"title", "artistNames"},
                    //            new int[]{R.id.list_item_title_textview, R.id.list_item_artists_textview},
                    //            android.widget.SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
                    //this.listView.setAdapter(adapter);
                    this.listView.setAdapter(new MyCursorAdapter(getActivity(), arrayListCursor, 0));
                    this.listView.setOnItemClickListener(createListViewClickListener());
                }
            }
        }


        //////////////////////////////////////////////////////////////////////
        // methods unique to this class (non-overrides)
        //////////////////////////////////////////////////////////////////////


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

        private void handleMessageFromMediaPlayerService(android.os.Message msg)
        {
            if (msg.what == MediaPlayerService.MessagesToClient.UNRECOGNIZED_REQUEST.ordinal()) {
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(UNRECOGNIZED_REQUEST)");
            }
            else if (msg.what == MediaPlayerService.MessagesToClient.ERROR_PLAYING_STREAM.ordinal()) {
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(ERROR_PLAYING_STREAM)");
            }
            else if (msg.what == MediaPlayerService.MessagesToClient.BUFFERING_UPDATE.ordinal()) {
                android.os.Bundle messageData = msg.getData();
                final String path = messageData.getString("message");
                Integer percentValue = null;
                if (messageData.containsKey("percent")) {
                    percentValue = messageData.getInt("percent");
                }
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(BUFFERING_UPDATE) - " + percentValue + "%");

                if ( (queuedAudioPath != null)
                  && (queuedAudioPath.equals(path))
                  && (percentValue == 100) )
                {
                    // an audio segment that we requested to be played has begun.  We started it at zero volume, so fade-in
                    android.util.Log.d(LOG_TAG, "queued audio starting to play...");
                    queuedAudioPath = null;

                    //AsyncTask.execute(
                    //    new Runnable() {
                    //        @Override
                    //        public void run() {
                                android.util.Log.d(LOG_TAG, "entering fade-in thread...");
                                android.os.Message outgoingMessage = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.FADE_IN);
                                android.os.Bundle outgoingMessageData = outgoingMessage.getData();
                                outgoingMessageData.putString("path", path);
                                try {
                                    sendMessageToMediaPlayerService(outgoingMessage);
                                } catch (Exception e) {
                                    android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                                }
                    //        }
                    //    } );
                }
            }
            else if (msg.what == MediaPlayerService.MessagesToClient.READY_FOR_PLAYBACK.ordinal()) {
                // if the service was able to determine the duration of the stream, it will be in the "duration" field
                android.os.Bundle messageData = msg.getData();
                final String path = messageData.getString("message");
                final int duration = messageData.getInt("duration");
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(READY_FOR_PLAYBACK) - duration=" + duration);
/*
                // set a timer to fade the audio to silence once it has almost completed playback
                android.os.Message outgoingMessage = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.SCHEDULED_FADE_OUT);
                android.os.Bundle outgoingMessageData = outgoingMessage.getData();
                outgoingMessageData.putString("path", path);
                outgoingMessageData.putInt("after", duration-1000);
                try {
                    sendMessageToMediaPlayerService(outgoingMessage);
                } catch (Exception e) {
                    android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                }
//
*/
                android.os.AsyncTask.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            android.util.Log.d(LOG_TAG, "entering fade-out thread...");
                                try {
                                    java.lang.Thread.sleep(duration-1000, 0);
                                } catch (InterruptedException ie) {}

                            //android.widget.Toast.makeText(getActivity(), "about to end...", android.widget.Toast.LENGTH_SHORT);
                            android.util.Log.d(LOG_TAG, "playback about to end...");

                            android.os.Message outgoingMessage = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.FADE_OUT);
                            android.os.Bundle outgoingMessageData = outgoingMessage.getData();
                            outgoingMessageData.putString("path", path);
                            try {
                                sendMessageToMediaPlayerService(outgoingMessage);
                            } catch (Exception e) {
                                android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                            }

                        }
                    } );

            }
            else if (msg.what == MediaPlayerService.MessagesToClient.PLAYING_STREAM_COMPLETE.ordinal()) {
                android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(PLAYING_STREAM_COMPLETE)");

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

                    android.util.Log.d(LOG_TAG, "preview url: " + cursor.getString(cursor.getColumnIndex("previewUrl")));
//                    android.content.Intent serviceIntent = new android.content.Intent(getActivity(), com.josephbanta.avjukebox.service.MediaPlayerService.class);
//                    serviceIntent.putExtra("path", cursor.getString(cursor.getColumnIndex("previewUrl")));
//                    serviceIntent.putExtra("title", cursor.getString(cursor.getColumnIndex("title")));
//                    getActivity().startService(serviceIntent);
                    android.widget.ProgressBar progressBar = (android.widget.ProgressBar)view.findViewById(R.id.progressBar);
                    progressBar.setVisibility(View.VISIBLE);

                    android.os.Message message = createMessageToMediaPlayerService(MediaPlayerService.MessagesToService.PLAY_STREAM);
                    android.os.Bundle bundle = message.getData();
                    String path = cursor.getString(cursor.getColumnIndex("previewUrl"));
                    bundle.putString("title", cursor.getString(cursor.getColumnIndex("title")));
                    bundle.putString("path", path);
                    bundle.putFloat("volume", 0.0f);
                    try {
                        sendMessageToMediaPlayerService(message);
                    } catch (Exception e) {
                        android.util.Log.d(LOG_TAG, "Exception messaging player service: " + e);
                    }
                    queuedAudioPath = path;
                }
            };
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
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                mediaPlayerServiceMessageSender = null;
                boundToService = false;
            }
        } // private class MediaPlayerServiceConnection

        private final class MyCursorAdapter extends android.widget.CursorAdapter {
            com.nostra13.universalimageloader.core.ImageLoader imageLoader = com.nostra13.universalimageloader.core.ImageLoader.getInstance();
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

            MyCursorAdapter(android.content.Context context, android.database.Cursor cursor, int flags) {
                super(context, cursor, flags);

                mInflater = LayoutInflater.from(context);
            }

            @Override
            public View newView(android.content.Context context, android.database.Cursor cursor, ViewGroup parent) {
                View v = mInflater.inflate(R.layout.search_result_list_item, parent, false);
                return v;
            }

            @Override
            public void bindView(View view, android.content.Context context, android.database.Cursor cursor) {
                android.widget.TextView listItemTitle = (android.widget.TextView) view.findViewById(R.id.list_item_title_textview);
                android.widget.TextView listItemArtist = (android.widget.TextView) view.findViewById(R.id.list_item_artists_textview);
                android.widget.ImageView listItemIcon = (android.widget.ImageView) view.findViewById(R.id.list_item_icon);
                android.widget.ProgressBar listItemProgressBar = (android.widget.ProgressBar) view.findViewById(R.id.progressBar);

                listItemTitle.setText(cursor.getString(cursor.getColumnIndex("title")));
                listItemArtist.setText(cursor.getString(cursor.getColumnIndex("artistNames")));

                imageLoader.displayImage(cursor.getString(cursor.getColumnIndex("iconUrl")),
                        listItemIcon, options);
                listItemProgressBar.setVisibility(View.GONE);
            }

            LayoutInflater mInflater;
        } // private final class MyCursorAdapter

    }

}



/*
{
  "tracks" : {
    "href" : "https://api.spotify.com/v1/search?query=Madonna&offset=0&limit=20&type=track",
    "items" : [ {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 342680,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903606"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/1z3ugFmUKoCzGsI6jdY4Ci"
      },
      "href" : "https://api.spotify.com/v1/tracks/1z3ugFmUKoCzGsI6jdY4Ci",
      "id" : "1z3ugFmUKoCzGsI6jdY4Ci",
      "name" : "Like A Prayer",
      "popularity" : 71,
      "preview_url" : "https://p.scdn.co/mp3-preview/e3f2c751d537788f5b0c5a43b0d0b4a5aa23ae26",
      "track_number" : 9,
      "type" : "track",
      "uri" : "spotify:track:1z3ugFmUKoCzGsI6jdY4Ci"
    }, {
      "album" : {
        "album_type" : "single",
        "available_markets" : [ "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CH", "CL", "CO", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GR", "GT", "HK", "HN", "HU", "IS", "IT", "LT", "LU", "LV", "MT", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/2D2sDow81aEsIKYceXJAWe"
        },
        "href" : "https://api.spotify.com/v1/albums/2D2sDow81aEsIKYceXJAWe",
        "id" : "2D2sDow81aEsIKYceXJAWe",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/b6c37af83354d41cde85902101925eeab7ffca9b",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/1e810dbb5a3ebba551a949dfdf215b3eb3dd6939",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/4e14596f887831b476e87d82746fb10280634102",
          "width" : 64
        } ],
        "name" : "Girl Gone Wild (Remixes)",
        "type" : "album",
        "uri" : "spotify:album:2D2sDow81aEsIKYceXJAWe"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CH", "CL", "CO", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GR", "GT", "HK", "HN", "HU", "IS", "IT", "LT", "LU", "LV", "MT", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW" ],
      "disc_number" : 1,
      "duration_ms" : 316080,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USUG11200794"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/2keYh2K1DyQybEzUGfeGsS"
      },
      "href" : "https://api.spotify.com/v1/tracks/2keYh2K1DyQybEzUGfeGsS",
      "id" : "2keYh2K1DyQybEzUGfeGsS",
      "name" : "Madonna vs. Avicii – Girl Gone Wild - AVICII's UMF Mix",
      "popularity" : 56,
      "preview_url" : "https://p.scdn.co/mp3-preview/4560cbf27f72ff5f7cdb6a3a1a6c5cce623d935c",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:2keYh2K1DyQybEzUGfeGsS"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 242946,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903618"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/2Iib2MV3ECFJAourgP9dlY"
      },
      "href" : "https://api.spotify.com/v1/tracks/2Iib2MV3ECFJAourgP9dlY",
      "id" : "2Iib2MV3ECFJAourgP9dlY",
      "name" : "La Isla Bonita",
      "popularity" : 66,
      "preview_url" : "https://p.scdn.co/mp3-preview/09edf195c6fd3d39eda21242dd885b64042e3fb3",
      "track_number" : 21,
      "type" : "track",
      "uri" : "spotify:track:2Iib2MV3ECFJAourgP9dlY"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 189693,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903601"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/1oHClQEgDmmbcEx12Kc5nZ"
      },
      "href" : "https://api.spotify.com/v1/tracks/1oHClQEgDmmbcEx12Kc5nZ",
      "id" : "1oHClQEgDmmbcEx12Kc5nZ",
      "name" : "4 Minutes - feat. Justin Timberlake And Timbaland",
      "popularity" : 67,
      "preview_url" : "https://p.scdn.co/mp3-preview/8e58d4fdaf32e59d4ae59d2178eb049d624b8deb",
      "track_number" : 4,
      "type" : "track",
      "uri" : "spotify:track:1oHClQEgDmmbcEx12Kc5nZ"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 188280,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903604"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/1iaIEmZHrjGzWUmJ9BaFr6"
      },
      "href" : "https://api.spotify.com/v1/tracks/1iaIEmZHrjGzWUmJ9BaFr6",
      "id" : "1iaIEmZHrjGzWUmJ9BaFr6",
      "name" : "Like A Virgin",
      "popularity" : 65,
      "preview_url" : "https://p.scdn.co/mp3-preview/a4885223b94761b000a943671f367233af3be480",
      "track_number" : 7,
      "type" : "track",
      "uri" : "spotify:track:1iaIEmZHrjGzWUmJ9BaFr6"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 336880,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903598"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/70VjMtwhm3Q2HBQeJnPfmh"
      },
      "href" : "https://api.spotify.com/v1/tracks/70VjMtwhm3Q2HBQeJnPfmh",
      "id" : "70VjMtwhm3Q2HBQeJnPfmh",
      "name" : "Hung Up",
      "popularity" : 64,
      "preview_url" : "https://p.scdn.co/mp3-preview/201bd08d8cdf147fdb7f4fd1183056484e056f3e",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:70VjMtwhm3Q2HBQeJnPfmh"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 240280,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903617"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/22sLuJYcvZOSoLLRYev1s5"
      },
      "href" : "https://api.spotify.com/v1/tracks/22sLuJYcvZOSoLLRYev1s5",
      "id" : "22sLuJYcvZOSoLLRYev1s5",
      "name" : "Material Girl",
      "popularity" : 66,
      "preview_url" : "https://p.scdn.co/mp3-preview/bd309c9b1d3fdbc9253cf17dc54d3457f39127dc",
      "track_number" : 20,
      "type" : "track",
      "uri" : "spotify:track:22sLuJYcvZOSoLLRYev1s5"
    }, {
      "album" : {
        "album_type" : "single",
        "available_markets" : [ "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/4FnBBsv3qZgaQ4MpVS8qPf"
        },
        "href" : "https://api.spotify.com/v1/albums/4FnBBsv3qZgaQ4MpVS8qPf",
        "id" : "4FnBBsv3qZgaQ4MpVS8qPf",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/bda7e0b02d8b08110696d6f7fd117fa02c0ff7cb",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/14f9f4dc6f3677074053202e7d5098b640e4d853",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/6a1e71b1702761614a32d30f435e10c27250a557",
          "width" : 64
        } ],
        "name" : "Revolver",
        "type" : "album",
        "uri" : "spotify:album:4FnBBsv3qZgaQ4MpVS8qPf"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 179693,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10905087"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/3HgCb6lSTWjEAnWFRdvIMw"
      },
      "href" : "https://api.spotify.com/v1/tracks/3HgCb6lSTWjEAnWFRdvIMw",
      "id" : "3HgCb6lSTWjEAnWFRdvIMw",
      "name" : "Revolver - Madonna vs. David Guetta One Love Remix",
      "popularity" : 55,
      "preview_url" : "https://p.scdn.co/mp3-preview/e2ed2bc5e8541fd74d07d92fd20f72ac5ca07ebb",
      "track_number" : 2,
      "type" : "track",
      "uri" : "spotify:track:3HgCb6lSTWjEAnWFRdvIMw"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 285093,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903605"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/2m0M7YqCy4lXfedh18qd8N"
      },
      "href" : "https://api.spotify.com/v1/tracks/2m0M7YqCy4lXfedh18qd8N",
      "id" : "2m0M7YqCy4lXfedh18qd8N",
      "name" : "Into The Groove",
      "popularity" : 64,
      "preview_url" : "https://p.scdn.co/mp3-preview/eae6148ae8f641f105f8451d72f5757c468da3f5",
      "track_number" : 8,
      "type" : "track",
      "uri" : "spotify:track:2m0M7YqCy4lXfedh18qd8N"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 316813,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903600"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/27QvYgBk0CHOVHthWnkuWt"
      },
      "href" : "https://api.spotify.com/v1/tracks/27QvYgBk0CHOVHthWnkuWt",
      "id" : "27QvYgBk0CHOVHthWnkuWt",
      "name" : "Vogue",
      "popularity" : 64,
      "preview_url" : "https://p.scdn.co/mp3-preview/5dff890c52390543d456fa262ae167b79c6cd389",
      "track_number" : 3,
      "type" : "track",
      "uri" : "spotify:track:27QvYgBk0CHOVHthWnkuWt"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/48AGkmM7iO4jrELRnNZGPV"
        },
        "href" : "https://api.spotify.com/v1/albums/48AGkmM7iO4jrELRnNZGPV",
        "id" : "48AGkmM7iO4jrELRnNZGPV",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/e41ccd612f1f0d3de08f2ca147f32efb16e0f56a",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/27428b28f68db617679aa5cb9822054293bb9b52",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/92a1b30e71441c33bc96ddcbeeb4bf492b5ff25b",
          "width" : 64
        } ],
        "name" : "Like A Prayer",
        "type" : "album",
        "uri" : "spotify:album:48AGkmM7iO4jrELRnNZGPV"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 340866,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10002775"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/2v7ywbUzCgcVohHaKUcacV"
      },
      "href" : "https://api.spotify.com/v1/tracks/2v7ywbUzCgcVohHaKUcacV",
      "id" : "2v7ywbUzCgcVohHaKUcacV",
      "name" : "Like A Prayer",
      "popularity" : 63,
      "preview_url" : "https://p.scdn.co/mp3-preview/70aff83ce640bab62db1b050d4997c759728f758",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:2v7ywbUzCgcVohHaKUcacV"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 378840,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903624"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/0YT8MKXuaRdhOnDG9Jm1Dv"
      },
      "href" : "https://api.spotify.com/v1/tracks/0YT8MKXuaRdhOnDG9Jm1Dv",
      "id" : "0YT8MKXuaRdhOnDG9Jm1Dv",
      "name" : "Frozen",
      "popularity" : 62,
      "preview_url" : "https://p.scdn.co/mp3-preview/897cfdcc5e3a314db769998c4d04e9e515402c3f",
      "track_number" : 27,
      "type" : "track",
      "uri" : "spotify:track:0YT8MKXuaRdhOnDG9Jm1Dv"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/2IU9ftOgyRL2caQGWK1jjX"
        },
        "href" : "https://api.spotify.com/v1/albums/2IU9ftOgyRL2caQGWK1jjX",
        "id" : "2IU9ftOgyRL2caQGWK1jjX",
        "images" : [ {
          "height" : 635,
          "url" : "https://i.scdn.co/image/0d8162cc6aa964fd833b393c7103333e1267d087",
          "width" : 640
        }, {
          "height" : 298,
          "url" : "https://i.scdn.co/image/542cbe40a62f21e426549370628cf998ab47e7e1",
          "width" : 300
        }, {
          "height" : 63,
          "url" : "https://i.scdn.co/image/c43786004efad5c3c126673886348e8ff1a248b1",
          "width" : 64
        } ],
        "name" : "Like A Virgin",
        "type" : "album",
        "uri" : "spotify:album:2IU9ftOgyRL2caQGWK1jjX"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 218626,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10002748"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/1ZPlNanZsJSPK5h9YZZFbZ"
      },
      "href" : "https://api.spotify.com/v1/tracks/1ZPlNanZsJSPK5h9YZZFbZ",
      "id" : "1ZPlNanZsJSPK5h9YZZFbZ",
      "name" : "Like A Virgin",
      "popularity" : 63,
      "preview_url" : "https://p.scdn.co/mp3-preview/e4c1c9d235a64df128bdf361227d002d8ff8bcb2",
      "track_number" : 3,
      "type" : "track",
      "uri" : "spotify:track:1ZPlNanZsJSPK5h9YZZFbZ"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/6cuNyrSmRjBeekioLdLkvI"
        },
        "href" : "https://api.spotify.com/v1/albums/6cuNyrSmRjBeekioLdLkvI",
        "id" : "6cuNyrSmRjBeekioLdLkvI",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/246565c45ea4085d5b3889619fa1112ec6d42eed",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/f89849d36862a9dd2807be1d6d07eb0159c26673",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/cfa2d86696ff7cd8ea862f50ed05d086f1d66521",
          "width" : 64
        } ],
        "name" : "Ray Of Light",
        "type" : "album",
        "uri" : "spotify:album:6cuNyrSmRjBeekioLdLkvI"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 250560,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB19701744"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/01VFDkHBNJcCNUjzD3flWg"
      },
      "href" : "https://api.spotify.com/v1/tracks/01VFDkHBNJcCNUjzD3flWg",
      "id" : "01VFDkHBNJcCNUjzD3flWg",
      "name" : "The Power Of Good-Bye",
      "popularity" : 55,
      "preview_url" : "https://p.scdn.co/mp3-preview/80c898415a0da8aacfb466158a54f8e841c96b03",
      "track_number" : 10,
      "type" : "track",
      "uri" : "spotify:track:01VFDkHBNJcCNUjzD3flWg"
    }, {
      "album" : {
        "album_type" : "single",
        "available_markets" : [ "CA", "TR", "US" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/6AraDLFb8gFQCbGi0ymsaz"
        },
        "href" : "https://api.spotify.com/v1/albums/6AraDLFb8gFQCbGi0ymsaz",
        "id" : "6AraDLFb8gFQCbGi0ymsaz",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/15ca4e3f2ccdc74b005b8c74b5d2f47cc9e38bef",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/dd52b2737c6e4db784869e5400d095e41c7885d9",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/a5c38c9f700f0567f599731f1ea5206a462b6c23",
          "width" : 64
        } ],
        "name" : "Madonna (And Other Mothers In The Hood) [feat. Nikki Jean]",
        "type" : "album",
        "uri" : "spotify:album:6AraDLFb8gFQCbGi0ymsaz"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/01QTIT5P1pFP3QnnFSdsJf"
        },
        "href" : "https://api.spotify.com/v1/artists/01QTIT5P1pFP3QnnFSdsJf",
        "id" : "01QTIT5P1pFP3QnnFSdsJf",
        "name" : "Lupe Fiasco",
        "type" : "artist",
        "uri" : "spotify:artist:01QTIT5P1pFP3QnnFSdsJf"
      }, {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/5LkHiburgp40vnKs1PFQYR"
        },
        "href" : "https://api.spotify.com/v1/artists/5LkHiburgp40vnKs1PFQYR",
        "id" : "5LkHiburgp40vnKs1PFQYR",
        "name" : "Nikki Jean",
        "type" : "artist",
        "uri" : "spotify:artist:5LkHiburgp40vnKs1PFQYR"
      } ],
      "available_markets" : [ "CA", "TR", "US" ],
      "disc_number" : 1,
      "duration_ms" : 282852,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USAT21405281"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/39WaCxR99Xmw0Vrut09rUB"
      },
      "href" : "https://api.spotify.com/v1/tracks/39WaCxR99Xmw0Vrut09rUB",
      "id" : "39WaCxR99Xmw0Vrut09rUB",
      "name" : "Madonna (And Other Mothers In The Hood) [feat. Nikki Jean]",
      "popularity" : 0,
      "preview_url" : "https://p.scdn.co/mp3-preview/1240a268856f2909521a273a03c7363b43ac3625",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:39WaCxR99Xmw0Vrut09rUB"
    }, {
      "album" : {
        "album_type" : "single",
        "available_markets" : [ "AD", "AT", "AU", "BE", "BG", "CA", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HK", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NL", "NO", "NZ", "PH", "PL", "PT", "RO", "SE", "SG", "SI", "SK", "TR", "TW", "US" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/7pHmlYTOzc4z1T4FBh6wU2"
        },
        "href" : "https://api.spotify.com/v1/albums/7pHmlYTOzc4z1T4FBh6wU2",
        "id" : "7pHmlYTOzc4z1T4FBh6wU2",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/c32fc205cab138e8cd2dac0f64855764cc723f69",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/301cd606322beb75b441b961641a2433d2c91bea",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/adb67ac0755e02a89fed66b8b9d7a7d865ff8c80",
          "width" : 64
        } ],
        "name" : "Madonna (And Other Mothers In The Hood) [feat. Nikki Jean]",
        "type" : "album",
        "uri" : "spotify:album:7pHmlYTOzc4z1T4FBh6wU2"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/01QTIT5P1pFP3QnnFSdsJf"
        },
        "href" : "https://api.spotify.com/v1/artists/01QTIT5P1pFP3QnnFSdsJf",
        "id" : "01QTIT5P1pFP3QnnFSdsJf",
        "name" : "Lupe Fiasco",
        "type" : "artist",
        "uri" : "spotify:artist:01QTIT5P1pFP3QnnFSdsJf"
      }, {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/5LkHiburgp40vnKs1PFQYR"
        },
        "href" : "https://api.spotify.com/v1/artists/5LkHiburgp40vnKs1PFQYR",
        "id" : "5LkHiburgp40vnKs1PFQYR",
        "name" : "Nikki Jean",
        "type" : "artist",
        "uri" : "spotify:artist:5LkHiburgp40vnKs1PFQYR"
      } ],
      "available_markets" : [ "AD", "AT", "AU", "BE", "BG", "CA", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HK", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NL", "NO", "NZ", "PH", "PL", "PT", "RO", "SE", "SG", "SI", "SK", "TR", "TW", "US" ],
      "disc_number" : 1,
      "duration_ms" : 282859,
      "explicit" : true,
      "external_ids" : {
        "isrc" : "USAT21405250"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/7388va7xcch4C4nUWpvqN7"
      },
      "href" : "https://api.spotify.com/v1/tracks/7388va7xcch4C4nUWpvqN7",
      "id" : "7388va7xcch4C4nUWpvqN7",
      "name" : "Madonna (And Other Mothers In The Hood) [feat. Nikki Jean]",
      "popularity" : 0,
      "preview_url" : "https://p.scdn.co/mp3-preview/aecb93d0bdf582f489e8a5c70da09bd14dc3a5c5",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:7388va7xcch4C4nUWpvqN7"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AT", "AU", "BE", "CH", "DE", "DK", "ES", "FI", "FR", "GB", "NL", "NO", "NZ", "SE" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/2trAegxlPgPnZHfYrUNvp0"
        },
        "href" : "https://api.spotify.com/v1/albums/2trAegxlPgPnZHfYrUNvp0",
        "id" : "2trAegxlPgPnZHfYrUNvp0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/52de03b69ce23c39d428da902bcf753f3584ac27",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/d1aa8a6a871c6552a8da289525ef81a5615fd40f",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/41e7bd8b25d6ba5a1ea1139fba7ab66e75d53141",
          "width" : 64
        } ],
        "name" : "MDNA",
        "type" : "album",
        "uri" : "spotify:album:2trAegxlPgPnZHfYrUNvp0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AT", "AU", "BE", "CH", "DE", "DK", "ES", "FI", "FR", "GB", "NL", "NO", "NZ", "SE" ],
      "disc_number" : 1,
      "duration_ms" : 223066,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USUG11200444"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/3ZfMci7n6qk9nRodX8BfIb"
      },
      "href" : "https://api.spotify.com/v1/tracks/3ZfMci7n6qk9nRodX8BfIb",
      "id" : "3ZfMci7n6qk9nRodX8BfIb",
      "name" : "Girl Gone Wild",
      "popularity" : 52,
      "preview_url" : "https://p.scdn.co/mp3-preview/6ae6b417e7886273f11368b09b29414a3140c541",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:3ZfMci7n6qk9nRodX8BfIb"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 239093,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903609"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/6ioBgySxoeQKALvAeLEmId"
      },
      "href" : "https://api.spotify.com/v1/tracks/6ioBgySxoeQKALvAeLEmId",
      "id" : "6ioBgySxoeQKALvAeLEmId",
      "name" : "Express Yourself",
      "popularity" : 57,
      "preview_url" : "https://p.scdn.co/mp3-preview/1d03109355005557bba78fa888170596ac093687",
      "track_number" : 12,
      "type" : "track",
      "uri" : "spotify:track:6ioBgySxoeQKALvAeLEmId"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/43lok9zd7BW5CoYkXZs7S0"
        },
        "href" : "https://api.spotify.com/v1/albums/43lok9zd7BW5CoYkXZs7S0",
        "id" : "43lok9zd7BW5CoYkXZs7S0",
        "images" : [ {
          "height" : 640,
          "url" : "https://i.scdn.co/image/006c443e86ad04f6ce8f9ade1da24e9b6f2e2132",
          "width" : 640
        }, {
          "height" : 300,
          "url" : "https://i.scdn.co/image/a7f68aad3334606ab2030c026d5017c3a9f6a251",
          "width" : 300
        }, {
          "height" : 64,
          "url" : "https://i.scdn.co/image/f15e8fcdee463c756c769ef5291840050ae6ae11",
          "width" : 64
        } ],
        "name" : "Celebration",
        "type" : "album",
        "uri" : "spotify:album:43lok9zd7BW5CoYkXZs7S0"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 269573,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10903619"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/0Oa9Qtd0FuhcmLi3sWTF9F"
      },
      "href" : "https://api.spotify.com/v1/tracks/0Oa9Qtd0FuhcmLi3sWTF9F",
      "id" : "0Oa9Qtd0FuhcmLi3sWTF9F",
      "name" : "Papa Don't Preach",
      "popularity" : 58,
      "preview_url" : "https://p.scdn.co/mp3-preview/cec511b071b1ce9c0326030c68d77d811e9634f5",
      "track_number" : 22,
      "type" : "track",
      "uri" : "spotify:track:0Oa9Qtd0FuhcmLi3sWTF9F"
    }, {
      "album" : {
        "album_type" : "album",
        "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
        "external_urls" : {
          "spotify" : "https://open.spotify.com/album/2IU9ftOgyRL2caQGWK1jjX"
        },
        "href" : "https://api.spotify.com/v1/albums/2IU9ftOgyRL2caQGWK1jjX",
        "id" : "2IU9ftOgyRL2caQGWK1jjX",
        "images" : [ {
          "height" : 635,
          "url" : "https://i.scdn.co/image/0d8162cc6aa964fd833b393c7103333e1267d087",
          "width" : 640
        }, {
          "height" : 298,
          "url" : "https://i.scdn.co/image/542cbe40a62f21e426549370628cf998ab47e7e1",
          "width" : 300
        }, {
          "height" : 63,
          "url" : "https://i.scdn.co/image/c43786004efad5c3c126673886348e8ff1a248b1",
          "width" : 64
        } ],
        "name" : "Like A Virgin",
        "type" : "album",
        "uri" : "spotify:album:2IU9ftOgyRL2caQGWK1jjX"
      },
      "artists" : [ {
        "external_urls" : {
          "spotify" : "https://open.spotify.com/artist/6tbjWDEIzxoDsBA1FuhfPW"
        },
        "href" : "https://api.spotify.com/v1/artists/6tbjWDEIzxoDsBA1FuhfPW",
        "id" : "6tbjWDEIzxoDsBA1FuhfPW",
        "name" : "Madonna",
        "type" : "artist",
        "uri" : "spotify:artist:6tbjWDEIzxoDsBA1FuhfPW"
      } ],
      "available_markets" : [ "AD", "AR", "AT", "AU", "BE", "BG", "BO", "BR", "CA", "CH", "CL", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "EC", "EE", "ES", "FI", "FR", "GB", "GR", "GT", "HK", "HN", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MT", "MX", "MY", "NI", "NL", "NO", "NZ", "PA", "PE", "PH", "PL", "PT", "PY", "RO", "SE", "SG", "SI", "SK", "SV", "TR", "TW", "US", "UY" ],
      "disc_number" : 1,
      "duration_ms" : 240706,
      "explicit" : false,
      "external_ids" : {
        "isrc" : "USWB10002746"
      },
      "external_urls" : {
        "spotify" : "https://open.spotify.com/track/7bkyXSi4GtVfD7itZRUR3e"
      },
      "href" : "https://api.spotify.com/v1/tracks/7bkyXSi4GtVfD7itZRUR3e",
      "id" : "7bkyXSi4GtVfD7itZRUR3e",
      "name" : "Material Girl",
      "popularity" : 61,
      "preview_url" : "https://p.scdn.co/mp3-preview/1dec3e6ae5a8eab344ed3f3e93456a719f5ecbac",
      "track_number" : 1,
      "type" : "track",
      "uri" : "spotify:track:7bkyXSi4GtVfD7itZRUR3e"
    } ],
    "limit" : 20,
    "next" : "https://api.spotify.com/v1/search?query=Madonna&offset=20&limit=20&type=track",
    "offset" : 0,
    "previous" : null,
    "total" : 14498
  }
}

*/
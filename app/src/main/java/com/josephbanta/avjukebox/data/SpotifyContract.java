package com.josephbanta.avjukebox.data;

/**
 * Created by JBanta on 12/13/14.
 */
public class SpotifyContract {
    // The "Content authority" is a name for the entire content provider, similar to the
    // relationship between a domain name and its website.  A convenient string to use for the
    // content authority is the package name for the app, which is guaranteed to be unique on the
    // device.
    public static final String CONTENT_AUTHORITY = "com.josephbanta.avjukebox";

    // Possible paths (appended to base content URI for possible URI's)
    // For instance, content://com.example.android.sunshine.app/weather/ is a valid path for
    // looking at weather data. content://com.example.android.sunshine.app/givemeroot/ will fail,
    // as the ContentProvider hasn't been given any information on what to do with "givemeroot".
    // At least, let's hope not.  Don't be that dev, reader.  Don't be that dev.
    public static final String PATH_TRACK_SEARCH = "tracks";

    // Use CONTENT_AUTHORITY to create the base of all URI's which apps will use to contact
    // the content provider.
    public static final android.net.Uri BASE_CONTENT_URI = android.net.Uri.parse("content://" + CONTENT_AUTHORITY);

    // Inner class that defines the table contents of the track table
    public static final class TrackEntry implements android.provider.BaseColumns {

        public static final android.net.Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_TRACK_SEARCH).build();

        public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/" + CONTENT_AUTHORITY + "/" + PATH_TRACK_SEARCH;
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/" + CONTENT_AUTHORITY + "/" + PATH_TRACK_SEARCH;

        // Table name
        public static final String TABLE_NAME = "track";
    }
}

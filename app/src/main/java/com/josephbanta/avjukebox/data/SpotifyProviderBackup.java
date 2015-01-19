package com.josephbanta.avjukebox.data;


import com.josephbanta.avjukebox.SpotifyWebApiClient;

/**
 * Created by JBanta on 12/13/14.
 */
public class SpotifyProviderBackup extends android.content.ContentProvider
{
    // The URI Matcher used by this content provider.
    private static final android.content.UriMatcher sUriMatcher = buildUriMatcher();

    private static final int TRACK_SEARCH = 100;

    //////////////////////////////////////////////////////////////////////
    // methods unique to this class (non-overrides)
    //////////////////////////////////////////////////////////////////////

    private static android.content.UriMatcher buildUriMatcher() {
        // All paths added to the UriMatcher have a corresponding code to return when a match is
        // found.  The code passed into the constructor represents the code to return for the root
        // URI.  It's common to use NO_MATCH as the code for this case.
        final android.content.UriMatcher matcher = new android.content.UriMatcher(android.content.UriMatcher.NO_MATCH);

        final String authority = SpotifyContract.CONTENT_AUTHORITY;

        // For each type of URI you want to add, create a corresponding code.
//        matcher.addURI(authority, WeatherContract.PATH_WEATHER, WEATHER);
//        matcher.addURI(authority, WeatherContract.PATH_WEATHER + "/*", WEATHER_WITH_LOCATION);
//        matcher.addURI(authority, WeatherContract.PATH_WEATHER + "/*/*", WEATHER_WITH_LOCATION_AND_DATE);

//        matcher.addURI(authority, WeatherContract.PATH_LOCATION, LOCATION);
//        matcher.addURI(authority, WeatherContract.PATH_LOCATION + "/#", LOCATION_ID);

        matcher.addURI(authority, SpotifyContract.PATH_TRACK_SEARCH, TRACK_SEARCH);

        return matcher;
    }

    //////////////////////////////////////////////////////////////////////
    // methods overridden from abstract class
    //                  android.content.ContentProvider
    //////////////////////////////////////////////////////////////////////

    /**
     * Comment copied from
     *    http://developer.android.com/reference/android/content/ContentProvider.html#onCreate()
     *
     * Implement this to initialize your content provider on startup. This method is called for all
     * registered content providers on the application main thread at application launch time. It
     * must not perform lengthy operations, or application startup will be delayed.
     *
     * You should defer nontrivial initialization (such as opening, upgrading, and scanning
     * databases) until the content provider is used (via
     *    query(Uri, String[], String, String[], String), insert(Uri, ContentValues), etc).
     * Deferred initialization keeps application startup fast, avoids unnecessary work if the
     * provider turns out not to be needed, and stops database errors (such as a full disk) from
     * halting application launch.
     *
     * If you use SQLite, SQLiteOpenHelper is a helpful utility class that makes it easy to manage
     * databases, and will automatically defer opening until first use. If you do use
     * SQLiteOpenHelper, make sure to avoid calling getReadableDatabase() or getWritableDatabase()
     * from this method. (Instead, override onOpen(SQLiteDatabase) to initialize the database when
     * it is first opened.)
     *
     * @return true if the provider was successfully loaded, false otherwise
     */
    @Override
    public boolean onCreate() {
        // return true if the provider was successfully loaded, false otherwise
        return true;
    }

    /*
     * Comment copied from
     *    http://developer.android.com/reference/android/content/ContentProvider.html#getType(android.net.Uri)
     *
     * Implement this to handle requests for the MIME type of the data at the given URI. The
     * returned MIME type should start with vnd.android.cursor.item for a single record, or
     * vnd.android.cursor.dir/ for multiple items. This method can be called from multiple threads,
     * as described in Processes and Threads.
     *
     * Note that there are no permissions needed for an application to access this information; if
     * your content provider requires read and/or write permissions, or is not exported, all
     * applications can still call this method regardless of their access permissions. This allows
     * them to retrieve the MIME type for a URI when dispatching intents.
     *
     * @param uri	the URI to query
     *
     * @return a MIME type string, or null if there is no type.
     */
    @Override
    public String getType(android.net.Uri uri) {

        // Use the Uri Matcher to determine what kind of URI this is.
        final int match = sUriMatcher.match(uri);

        switch (match) {
            case TRACK_SEARCH:
                return SpotifyContract.TrackEntry.CONTENT_TYPE;
            //case WEATHER_WITH_LOCATION_AND_DATE:
            //    return WeatherContract.WeatherEntry.CONTENT_ITEM_TYPE;
            //case WEATHER_WITH_LOCATION:
            //    return WeatherContract.WeatherEntry.CONTENT_TYPE;
            //case WEATHER:
            //    return WeatherContract.WeatherEntry.CONTENT_TYPE;
            //case LOCATION:
            //    return WeatherContract.LocationEntry.CONTENT_TYPE;
            //case LOCATION_ID:
            //    return WeatherContract.LocationEntry.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    /**
     * Implement this to handle query requests from clients.
     * This method can be called from multiple threads, as described in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html#Threads">Processes
     * and Threads</a>.
     * <p>
     * Example client call:<p>
     * <pre>// Request a specific record.
     * Cursor managedCursor = managedQuery(
     ContentUris.withAppendedId(Contacts.People.CONTENT_URI, 2),
     projection,    // Which columns to return.
     null,          // WHERE clause.
     null,          // WHERE clause value substitution
     People.NAME + " ASC");   // Sort order.</pre>
     * Example implementation:<p>
     * <pre>// SQLiteQueryBuilder is a helper class that creates the
     // proper SQL syntax for us.
     SQLiteQueryBuilder qBuilder = new SQLiteQueryBuilder();

     // Set the table we're querying.
     qBuilder.setTables(DATABASE_TABLE_NAME);

     // If the query ends in a specific record number, we're
     // being asked for a specific record, so set the
     // WHERE clause in our query.
     if((URI_MATCHER.match(uri)) == SPECIFIC_MESSAGE){
     qBuilder.appendWhere("_id=" + uri.getPathLeafId());
     }

     // Make the query.
     Cursor c = qBuilder.query(mDb,
     projection,
     selection,
     selectionArgs,
     groupBy,
     having,
     sortOrder);
     c.setNotificationUri(getContext().getContentResolver(), uri);
     return c;</pre>
     *
     * @param uri The URI to query. This will be the full URI sent by the client;
     *      if the client is requesting a specific record, the URI will end in a record number
     *      that the implementation should parse and add to a WHERE or HAVING clause, specifying
     *      that _id value.
     * @param projection The list of columns to put into the cursor. If
     *      {@code null} all columns are included.
     * @param selection A selection criteria to apply when filtering rows.
     *      If {@code null} then all rows are included.
     * @param selectionArgs You may include ?s in selection, which will be replaced by
     *      the values from selectionArgs, in order that they appear in the selection.
     *      The values will be bound as Strings.
     * @param sortOrder How the rows in the cursor should be sorted.
     *      If {@code null} then the provider is free to define the sort order.
     * @return a Cursor or {@code null}.
     */
    @Override
    public android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Here's the switch statement that, given a URI, will determine what kind of request it is,
        // and query the data source accordingly.
        android.database.Cursor retCursor = null;
        switch (sUriMatcher.match(uri)) {
            // "weather/*/*"
            case TRACK_SEARCH:
            {
//                retCursor = searchTracks(uri, projection, sortOrder);
//                org.json.JSONArray jsonArray = SpotifyWebApiClient.searchTracks (selectionArgs[0]);
//                com.android.common.ArrayListCursor cursor = new com.android.common.ArrayListCursor(projection, (java.util.ArrayList<java.util.ArrayList>) list);
//                retCursor = cursor;
                break;
            }
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);

        return retCursor;
    }


    /**
     * Implement this to handle requests to insert a new row.
     * As a courtesy, call {@link android.content.ContentResolver#notifyChange(android.net.Uri ,android.database.ContentObserver) notifyChange()}
     * after inserting.
     * This method can be called from multiple threads, as described in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html#Threads">Processes
     * and Threads</a>.
     * @param uri The content:// URI of the insertion request. This must not be {@code null}.
     * @param values A set of column_name/value pairs to add to the database.
     *     This must not be {@code null}.
     * @return The URI for the newly inserted item.
     */
    @Override
    public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
        //return null;
    }

    /**
     * Implement this to handle requests to delete one or more rows.
     * The implementation should apply the selection clause when performing
     * deletion, allowing the operation to affect multiple rows in a directory.
     * As a courtesy, call {@link android.content.ContentResolver#notifyChange(android.net.Uri ,android.database.ContentObserver) notifyChange()}
     * after deleting.
     * This method can be called from multiple threads, as described in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html#Threads">Processes
     * and Threads</a>.
     *
     * <p>The implementation is responsible for parsing out a row ID at the end
     * of the URI, if a specific row is being deleted. That is, the client would
     * pass in <code>content://contacts/people/22</code> and the implementation is
     * responsible for parsing the record number (22) when creating a SQL statement.
     *
     * @param uri The full URI to query, including a row ID (if a specific record is requested).
     * @param selection An optional restriction to apply to rows when deleting.
     * @return The number of rows affected.
     * @throws android.database.SQLException
     */
    @Override
    public int delete(android.net.Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
        //return 0;
    }

    /**
     * Implement this to handle requests to update one or more rows.
     * The implementation should update all rows matching the selection
     * to set the columns according to the provided values map.
     * As a courtesy, call {@link android.content.ContentResolver#notifyChange(android.net.Uri ,android.database.ContentObserver) notifyChange()}
     * after updating.
     * This method can be called from multiple threads, as described in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html#Threads">Processes
     * and Threads</a>.
     *
     * @param uri The URI to query. This can potentially have a record ID if this
     * is an update request for a specific record.
     * @param values A set of column_name/value pairs to update in the database.
     *     This must not be {@code null}.
     * @param selection An optional filter to match rows to update.
     * @return the number of rows affected.
     */
    @Override
    public int update(android.net.Uri uri, android.content.ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
        //return 0;
    }
}

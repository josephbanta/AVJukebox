package com.josephbanta.avjukebox;

import android.net.Uri;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by JBanta on 12/9/14.
 */
public class SpotifyWebApiClient
{
    public static final String LOG_TAG = SpotifyWebApiClient.class.getSimpleName();

    public static //org.json.JSONArray
                  String searchTracks (String query)
    {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String responseString = null;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are avaiable at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast
            final String SPOTIFY_SEARCH_BASE_URL =
                    "https://api.spotify.com/v1/search?";
            final String QUERY_PARAM = "q";
            final String TYPE_PARAM = "type";

            Uri builtUri = Uri.parse(SPOTIFY_SEARCH_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, query)
                    .appendQueryParameter(TYPE_PARAM, "track")
                    .build();

            URL url = new URL(builtUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.connect();

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return null;
            }

            responseString = buffer.toString();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }

        /*
        org.json.JSONArray items = null;
        if (responseString != null) {
            try {
                org.json.JSONObject jsonResponse = new org.json.JSONObject(responseString);
                org.json.JSONObject tracks = jsonResponse.getJSONObject("tracks");
                items = tracks.getJSONArray("items");

                //Log.d(LOG_TAG, "got response '" + responseString+ "'");
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

        }

        return items;
        */
        return responseString;
    }


}

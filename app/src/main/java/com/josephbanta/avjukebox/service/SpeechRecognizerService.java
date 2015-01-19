package com.josephbanta.avjukebox.service;

import com.josephbanta.avjukebox.*;
import android.os.Bundle;


public class SpeechRecognizerService extends MessagableService {
    // inner classes
    public enum IncomingMessages {
        REGISTER_CLIENT,
        DEREGISTER_CLIENT,
        START_RECOGNIZING,
        STOP_RECOGNIZING
    }
    public enum OutgoingMessages {
        UNRECOGNIZED_REQUEST,
        READY_FOR_SPEECH,
        BEGINNING_OF_SPEECH,
        RMS_CHANGED,
        END_OF_SPEECH,
        PARTIAL_RESULTS,
        RECOGNIZED_TEXT,
        ERROR
    }

    // constants
    public final String LOG_TAG = SpeechRecognizerService.class.getSimpleName();

    // member variables
    android.app.NotificationManager mNotificationManager; // For showing and hiding our notification.

    private android.speech.SpeechRecognizer speechRecognizer;

    // Keeps track of all current registered clients.
    java.util.ArrayList<android.os.Messenger> mClients = new java.util.ArrayList<android.os.Messenger>();


    //////////////////////////////////////////////////////////////////////
    // methods overridden from class
    //                       android.app.Service
    //////////////////////////////////////////////////////////////////////

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startID) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        mNotificationManager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        this.speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
        this.speechRecognizer.setRecognitionListener(this.createSpeechRecognitionListener());
        // we don't start listening until we have a registered client

        android.util.Log.d(LOG_TAG, "SpeechRecognizerService.onCreate()");
        // Display a notification about us starting.
        //showNotification();
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNotificationManager.cancel(R.string.recog_service_started);

        if(this.speechRecognizer!=null){
            this.speechRecognizer.stopListening();
            this.speechRecognizer.cancel();
            this.speechRecognizer.destroy();
        }

        // Tell the user we stopped.
        //android.widget.Toast.makeText(this, "SpeechRecognizerService.onDestroy()", android.widget.Toast.LENGTH_SHORT).show();
        android.util.Log.d(LOG_TAG, "SpeechRecognizerService.onDestroy()");
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
        boolean messageHandled = true;

        if (msg.what == IncomingMessages.REGISTER_CLIENT.ordinal()) {
            mClients.add(msg.replyTo);
        }
        else if (msg.what == IncomingMessages.DEREGISTER_CLIENT.ordinal()) {
            mClients.remove(msg.replyTo);
        }
        else if (msg.what == IncomingMessages.START_RECOGNIZING.ordinal()) {
            //android.widget.Toast.makeText(this, "Received request to start recognizing", android.widget.Toast.LENGTH_SHORT).show();
            startOrContinueSpeechRecog();
        }
        else if (msg.what == IncomingMessages.STOP_RECOGNIZING.ordinal()) {
            //android.widget.Toast.makeText(this, "Received request to stop recognizing", android.widget.Toast.LENGTH_SHORT).show();
        }
        else {
            for (int i=mClients.size()-1; i>=0; i--) {
                try {
                    mClients.get(i).send(android.os.Message.obtain(null,
                            OutgoingMessages.UNRECOGNIZED_REQUEST.ordinal(), msg.what, 0));
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
    private void showNotification() {

        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.recog_service_started);

        // Set the icon, scrolling text and timestamp
        android.app.Notification notification = new android.app.Notification(com.josephbanta.avjukebox.R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        android.app.PendingIntent contentIntent = android.app.PendingIntent.getActivity(this, 0,
                new android.content.Intent(this, com.josephbanta.avjukebox.MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.recog_service_label),
                text, contentIntent);

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNotificationManager.notify(R.string.recog_service_started, notification);
    }

    private android.speech.RecognitionListener createSpeechRecognitionListener() {
        return new android.speech.RecognitionListener() {

            public void onReadyForSpeech(Bundle params)
            {
                android.util.Log.d(LOG_TAG, "RecognitionListener.onReadyForSpeech");
                broadcastMessageToRecogClients(OutgoingMessages.READY_FOR_SPEECH, null);
            }
            public void onBeginningOfSpeech()
            {
                android.util.Log.d(LOG_TAG, "RecognitionListener.onBeginningOfSpeech");

                // notify the main activity that text is being recognized
                broadcastMessageToRecogClients(OutgoingMessages.BEGINNING_OF_SPEECH, null);
                //setUIState(ActivityState.PARTIAL_INPUT_RECIEVED);
                //mainTextView.setText("");
            }
            public void onRmsChanged(float rmsdB)
            {
                //android.util.Log.d(TAG, "RecognitionListener.onRmsChanged");
                broadcastMessageToRecogClients(OutgoingMessages.RMS_CHANGED, new Float(rmsdB));
            }
            public void onBufferReceived(byte[] buffer)
            {
                android.util.Log.d(LOG_TAG, "RecognitionListener.onBufferReceived");
            }
            public void onEndOfSpeech()
            {
                android.util.Log.d(LOG_TAG, "RecognitionListener.onEndofSpeech");
                broadcastMessageToRecogClients(OutgoingMessages.END_OF_SPEECH, null);
            }
            public void onError(int error)
            {
                String errorMessage = ( (error ==android.speech.SpeechRecognizer.ERROR_AUDIO)                    ? "Audio recording error."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_CLIENT)                   ? "Other client side errors."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) ? "Insufficient permissions"
                                      : (error ==android.speech.SpeechRecognizer.ERROR_NETWORK)                  ? "Other network related errors."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT)          ? "Network operation timed out."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_NO_MATCH)                 ? "No recognition result matched."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY)          ? "RecognitionService busy."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_SERVER)                   ? "Server sends error status."
                                      : (error ==android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT)           ? "No speech input"
                                      :                                                                            ("Unknown error " + error) );
                android.util.Log.d(LOG_TAG,  "RecognitionListener.onError:: " + errorMessage);
                broadcastMessageToRecogClients(OutgoingMessages.ERROR, errorMessage);
                //mText.setText("error " + error);
            }
            public void onResults(Bundle results)
            {
                String str = new String();
                //android.util.Log.d(TAG, "onResults " + results);
                java.util.ArrayList<String> data = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                android.util.Log.d(LOG_TAG, "RecognitionListener.onResults(" + (((data==null) || (data.size() <= 0)) ? "" : data.get(0)) + ")");
                broadcastMessageToRecogClients(OutgoingMessages.RECOGNIZED_TEXT, data);
                //for (int i = 0; i < data.size(); i++)
                //{
                //    //          Log.d(TAG, "result " + data.get(i));
                //    str += ((i==0)?"":" OR ")+data.get(i);
                //}
                //android.util.Log.d(LOG_TAG, "RecognitionListener.onResults " + str);
//                onSpeech(data);
                //mText.setText("results: "+String.valueOf(data.size()));

                //startOrContinueSpeechRecog();
            }
            public void onPartialResults(Bundle partialResults)
            {
                java.util.ArrayList<String> data = partialResults.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                android.util.Log.d(LOG_TAG, "RecognitionListener.onPartialResults(" + (((data==null) || (data.size() <= 0)) ? "" : data.get(0)) + ")");
                broadcastMessageToRecogClients(OutgoingMessages.PARTIAL_RESULTS, data);
            }
            public void onEvent(int eventType, Bundle params)
            {
                android.util.Log.d(LOG_TAG, "RecognitionListener.onEvent " + eventType);
            }
        };
    }

    private void startOrContinueSpeechRecog() {
        android.content.Intent intent = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE,"voice.recognition.test");

        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
        android.util.Log.i(LOG_TAG, "speech recognition initialized");
    }


    private void broadcastMessageToRecogClients (OutgoingMessages messageType, java.io.Serializable data)
    {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                android.os.Message message = android.os.Message.obtain(null, messageType.ordinal(), 0, 0);
                message.getData().putSerializable("data", data);
                mClients.get(i).send(message);
            } catch (android.os.RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                mClients.remove(i);
            }
        }
    }
}

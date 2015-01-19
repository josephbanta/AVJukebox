package com.josephbanta.avjukebox.service;

// implementation copied
//   from http://developer.android.com/guide/components/bound-services.html
public abstract class MessagableService extends android.app.Service {

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends android.os.Handler {
        @Override
        public void handleMessage(android.os.Message message) {
            if (handleIncomingMessage(message) == false) {
                super.handleMessage(message);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final android.os.Messenger mMessenger = new android.os.Messenger(new IncomingHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        //android.widget.Toast.makeText(getApplicationContext(), "binding", android.widget.Toast.LENGTH_SHORT).show();
        return mMessenger.getBinder();
    }

    /**
     * The implementation of this method will be called when a bound client sends a message
     */
    public abstract boolean handleIncomingMessage(android.os.Message msg);
}


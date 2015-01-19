package com.josephbanta.avjukebox;

import com.josephbanta.avjukebox.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class ImageryActivity extends Activity {
    // constants
    public final String LOG_TAG = ImageryActivity.class.getSimpleName();

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    private static java.util.HashMap<String, String> savedUrls = new java.util.HashMap<String, String>();

    private int savedUrlIndex = -1;



    //////////////////////////////////////////////////////////////////////
    // instance variables
    //////////////////////////////////////////////////////////////////////

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    private MediaPlayerServiceConnection mConnection = new MediaPlayerServiceConnection();
    private android.os.Messenger         mediaPlayerServiceMessageSender = null;
    private android.os.Messenger         mediaPlayerServiceMessageReceiver = new android.os.Messenger(new MediaPlayerServiceMessageReceiver());
    private boolean                      boundToMediaPlayerService = false;

    com.nostra13.universalimageloader.core.ImageLoader imageLoader = com.nostra13.universalimageloader.core.ImageLoader.getInstance();
    com.nostra13.universalimageloader.core.DisplayImageOptions options = new com.nostra13.universalimageloader.core.DisplayImageOptions.Builder()
            //.showImageOnLoading(R.drawable.musical_note) // resource or drawable
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
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };



    //////////////////////////////////////////////////////////////////////
    // methods overridden from class
    //                   android.app.Activity
    //////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_imagery);

        android.widget.FrameLayout root = (android.widget.FrameLayout) findViewById( R.id.root_layout );
        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final android.widget.ImageView contentView = (android.widget.ImageView)(findViewById(R.id.fullscreen_content));

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //if (TOGGLE_ON_CLICK) {
                    //    mSystemUiHider.toggle();
                    //} else {
                    //    mSystemUiHider.show();
                    //}
                    finish(); // touching anywhere closes the activity!
                }
            };
        contentView.setOnClickListener(onClickListener);
        //this.setOnClickListener(onClickListener);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);


        com.nostra13.universalimageloader.cache.memory.MemoryCache memoryCache = imageLoader.getMemoryCache();
        //contentView.setAlpha(0.0f);
        showNextImage(contentView);
    }


    /**
     * Called after {@link #onCreate} &mdash; or after {@link #onRestart} when
     * the activity had been stopped, but is now again being displayed to the
     * user.  It will be followed by {@link #onResume}.
     * <p/>
     * <p><em>Derived classes must call through to the super class's
     * implementation of this method.  If they do not, an exception will be
     * thrown.</em></p>
     *
     * @see #onCreate
     * @see #onStop
     * @see #onResume
     */
    @Override
    protected void onStart() {
        super.onStart();

        android.content.Intent serviceIntent = new android.content.Intent(this, com.josephbanta.avjukebox.service.MediaPlayerService.class);

        // Bind to the service
        //bindService( serviceIntent,
        //             mConnection,
        //             android.content.Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();

        // Unbind from the service
        if (boundToMediaPlayerService) {
            try {
                android.os.Message outgoingMessage = createMessageToMediaPlayerService(com.josephbanta.avjukebox.service.MediaPlayerService.MessagesToService.DEREGISTER_CLIENT);
                outgoingMessage.replyTo = mediaPlayerServiceMessageReceiver;
                mediaPlayerServiceMessageSender.send(outgoingMessage);

            } catch (android.os.RemoteException e) {
            }

            unbindService(mConnection);
            boundToMediaPlayerService = false;
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }


    //////////////////////////////////////////////////////////////////////
    // methods unique to this class (non-overrides)
    //////////////////////////////////////////////////////////////////////

    public static void setSavedUrls (java.util.HashMap<String, String> savedUrlsParam) {
        ImageryActivity.savedUrls.clear();
        for (String key : savedUrlsParam.keySet()) {
            ImageryActivity.savedUrls.put(key, savedUrlsParam.get(key));
        }
    }

    private void showNextImage (android.widget.ImageView imageView) {
        Object[] savedUrlArray = savedUrls.values().toArray();
        String nextUrl = null;
        if (savedUrlIndex == -1) {
            nextUrl = (String)savedUrlArray[savedUrlIndex = 0];
        }
        else {
            // iterate through the saved urls until we find one that's different
            String previousUrl = (String)savedUrlArray[savedUrlIndex];
            int nextIndex = savedUrlIndex;
            while (nextUrl == null) {
                if (++nextIndex == savedUrlIndex) {
                    break;
                }
                else if (nextIndex == savedUrlArray.length) {
                    nextIndex = 0;
                }

                String thatIndexUrl = (String)savedUrlArray[nextIndex];
                if ( !(thatIndexUrl.equals(previousUrl)) ) {
                    savedUrlIndex = nextIndex;
                    nextUrl = thatIndexUrl;
                }
            }
        }

        if (nextUrl != null) {
            imageLoader.displayImage(
                    nextUrl,
                    imageView,
                    options,
                    new com.nostra13.universalimageloader.core.listener.ImageLoadingListener() {
                        @Override
                        public void onLoadingStarted(String imageUri, View view) {
                        }

                        @Override
                        public void onLoadingFailed(String imageUri, View view, com.nostra13.universalimageloader.core.assist.FailReason failReason) {
                        }

                        @Override
                        public void onLoadingComplete(String imageUri, View view, android.graphics.Bitmap loadedImage) {
                            animateImage((android.widget.ImageView)view);
                        }

                        @Override
                        public void onLoadingCancelled(String imageUri, View view) {
                        }
                    } );
        }
    }

    private void animateImage (final android.widget.ImageView imageView)
    {
        //view.
        //int imageWidth = loadedImage.getWidth(),
        //        imageHeight = loadedImage.getHeight();
        android.util.Log.d(LOG_TAG, "onLoadingComplete() - view is " + imageView.getWidth() + "x" + imageView.getHeight());

        android.view.animation.AnimationSet animationSet = new android.view.animation.AnimationSet(false);

        android.view.animation.AlphaAnimation alphaFadeInAnimation = new android.view.animation.AlphaAnimation(0.0f, 1.0f);
        alphaFadeInAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());
        alphaFadeInAnimation.setStartOffset(0);
        alphaFadeInAnimation.setDuration(2000);
        animationSet.addAnimation(alphaFadeInAnimation);

        // ...then -- much more slowly -- scale it up to a just slightly larger size...
        float ratioOfScalingAtSplashEnd = 1.2f,
                viewWidth = imageView.getWidth(),
                viewHeight = imageView.getHeight();
        int widthOfViewAfterScaling  = (int)(ratioOfScalingAtSplashEnd*viewWidth),
                heightOfViewAfterScaling = (int)(ratioOfScalingAtSplashEnd*viewHeight),
                halfWidthDifference      = (int)((widthOfViewAfterScaling - viewWidth)/2),
                halfHeightDifference     = (int)((heightOfViewAfterScaling - viewHeight)/2);
        android.view.animation.ScaleAnimation scaleUpMoreAnimation = new android.view.animation.ScaleAnimation(1.0f, ratioOfScalingAtSplashEnd, 1.0f, ratioOfScalingAtSplashEnd);
        scaleUpMoreAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator()); // LinearInterpolator
        scaleUpMoreAnimation.setStartOffset(0);
        scaleUpMoreAnimation.setDuration(10000);
        animationSet.addAnimation(scaleUpMoreAnimation);

        // ...while slowly translating the location of the image up and left, so give the impression that the center is remaining stationary
        android.view.animation.TranslateAnimation translateAnimation = new android.view.animation.TranslateAnimation(0.0f, -1*halfWidthDifference, 0.0f, -1*halfHeightDifference);
        translateAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator()); //LinearInterpolator
        translateAnimation.setStartOffset(0);
        translateAnimation.setDuration(10000);
        animationSet.addAnimation(translateAnimation);

        android.view.animation.AlphaAnimation alphaFadeOutAnimation = new android.view.animation.AlphaAnimation(1.0f, 0.0f);
        alphaFadeOutAnimation.setInterpolator(new android.view.animation.AccelerateInterpolator());
        alphaFadeOutAnimation.setStartOffset(8000);
        alphaFadeOutAnimation.setDuration(2000);
        animationSet.addAnimation(alphaFadeOutAnimation);

        animationSet.setFillAfter(true);
        animationSet.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                showNextImage(imageView);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        imageView.startAnimation(animationSet);
    }


    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }


    public void handleMessageFromMediaPlayerService(android.os.Message msg) {
        com.josephbanta.avjukebox.service.MediaPlayerService.MessagesToClient messageType = com.josephbanta.avjukebox.service.MediaPlayerService.MessagesToClient.values()[msg.what];
        android.util.Log.d(LOG_TAG, "handleMessageFromMediaPlayerService(" + messageType + ")");
    }

    private android.os.Message createMessageToMediaPlayerService (com.josephbanta.avjukebox.service.MediaPlayerService.MessagesToService messageType)
    {
        android.os.Message msg = android.os.Message.obtain(
                null,
                messageType.ordinal());
        msg.replyTo = mediaPlayerServiceMessageReceiver;

        return msg;
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
                        com.josephbanta.avjukebox.service.MediaPlayerService.MessagesToService.REGISTER_CLIENT.ordinal());
                msg.replyTo = mediaPlayerServiceMessageReceiver;

                // include the current preferences in the registration message
                android.content.SharedPreferences sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(ImageryActivity.this);
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

            boundToMediaPlayerService = true;
        }

        public void onServiceDisconnected(android.content.ComponentName className) {
            mediaPlayerServiceMessageSender = null;
            boundToMediaPlayerService = false;
        }
    } // private class MediaPlayerServiceConnection
}

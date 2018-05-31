package net.dhleong.vlcvideoview;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.Collections;
import java.util.List;

/**
 * @author dhleong
 */
public class VlcVideoView extends FrameLayout {
    static final String TAG = "VlcVideoView";

    public interface OnCompletionListener {
        /**
         * Called when the end of a media source
         * is reached during playback
         */
        void onCompletion(VlcVideoView view);
    }

    public interface OnErrorListener {
        /**
         * Called to indicate an error
         */
        void onError(VlcVideoView view);
    }

    public interface OnPreparedListener {
        /**
         * Called when the video is loaded and ready to play
         */
        void onPrepared(VlcVideoView view);
    }

    public interface OnLoadingStateChangedListener {

        /**
         * Called when the video's loading status changed,
         *  for example when you seek forward or backward
         *  and it has to buffer
         */
        void onLoadingStateChanged(VlcVideoView view, boolean isLoading);
    }

    public interface OnKeyInterceptListener {
        /**
         * Returns true if the key dispatch event should be consumed.
         */
        boolean onInterceptKeyEvent(KeyEvent event);
    }

    static LibVLC vlcInstance;

    private final IVLCVout.Callback vlcOutCallback = new IVLCVout.Callback() {
        @Override
        public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
            VlcVideoView.this.onNewLayout(vlcVout, width, height, visibleWidth, visibleHeight, sarNum, sarDen);
        }

        @Override public void onSurfacesCreated(IVLCVout vlcVout) { }

        @Override public void onSurfacesDestroyed(IVLCVout vlcVout) { }

        @Override public void onHardwareAccelerationError(IVLCVout vlcVout) { }
    };

    static final int STATE_IDLE = 0;
    static final int STATE_LOADING = 1;
    static final int STATE_PLAY_ON_LOAD = 2;
    static final int STATE_PLAYBACK = 3;
    static final int STATE_BUFFER = 4;
    static final int STATE_SEEK = 5;

    private MediaPlayer player;
    int state = STATE_IDLE;

    private FrameLayout surfaceContainer;
    private SurfaceView playerView;
    private SurfaceView subtitlesView;

    OnCompletionListener onCompletion;
    OnErrorListener onError;
    OnKeyInterceptListener onKeyIntercept;
    OnLoadingStateChangedListener onLoadingStateChangedListener;
    OnPreparedListener onPrepared;

    final Runnable dispatchNotLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            state = STATE_PLAYBACK;
            notifyLoading(false);
        }
    };

    public VlcVideoView(Context context) {
        super(context);
        init(context);
    }

    public VlcVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VlcVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public VlcVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        FrameLayout surfaceFrame = surfaceContainer = new FrameLayout(context);
        surfaceFrame.setForegroundGravity(Gravity.CLIP_HORIZONTAL | Gravity.CLIP_VERTICAL);

        surfaceFrame.addView(playerView = new SurfaceView(context));
        surfaceFrame.addView(subtitlesView = new SurfaceView(context));

        addView(surfaceFrame, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // release all held resources
        release();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final OnKeyInterceptListener onKeyIntercept = this.onKeyIntercept;
        if (onKeyIntercept != null && onKeyIntercept.onInterceptKeyEvent(event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    public boolean canSeek() {
        MediaPlayer player = this.player;
        return player != null && player.isSeekable();
    }

    /** @return in milliseconds */
    public long getCurrentPosition() {
        MediaPlayer player = this.player;
        if (player == null) {
            return 0;
        }
        return player.getTime();
    }

    /** @return in milliseconds */
    public long getDuration() {
        MediaPlayer player = this.player;
        if (player == null) {
            return 0;
        }
        return player.getLength();
    }

    public boolean isPlaying() {
        MediaPlayer player = this.player;
        return player != null && player.isPlaying();
    }

    public void play() {
        MediaPlayer player = this.player;
        if (player != null) {
            player.play();

            if (state == STATE_LOADING) {
                state = STATE_PLAY_ON_LOAD;
            }
        }
    }

    public void pause() {
        MediaPlayer player = this.player;
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        onCompletion = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        onError = listener;
    }

    public void setOnKeyInterceptListener(OnKeyInterceptListener listener) {
        // we have to be focusable to intercept
        setFocusable(listener != null);

        onKeyIntercept = listener;
    }

    public void setOnLoadingStateChangedListener(OnLoadingStateChangedListener listener) {
        onLoadingStateChangedListener = listener;
    }

    public void setOnPreparedListener(OnPreparedListener listener) {
        onPrepared = listener;
    }

    /**
     * @param mrl A VLC MRL
     * @see #setVideoUri(Uri)
     */
    public void setVideoMrl(@NonNull String mrl) {
        setVideoMrl(mrl, Collections.<String>emptyList());
    }

    /**
     * @param mrl A VLC MRL
     * @see #setVideoUri(Uri)
     * @see #setVideoUri(Uri, List)
     */
    public void setVideoMrl(@NonNull String mrl, List<String> options) {
        setMedia(new Media(getVlc(), mrl), options);
    }

    /**
     * @see #setVideoUri(Uri, List)
     */
    public void setVideoUri(@NonNull Uri uri) {
        setVideoUri(uri, Collections.<String>emptyList());
    }

    /**
     * Set the Uri for a video to load, optionally with
     *  some VLC-specific options. These are colon-prefixed
     *  options that would normally be passed on as cli args.
     *
     * To start playing as soon as the video is prepared,
     * just call {@link #play()} immediately after
     * calling this method. You can also listen
     * for the video prepared event via
     * {@link #setOnPreparedListener(OnPreparedListener)}
     */
    public void setVideoUri(@NonNull Uri uri, List<String> options) {
        setMedia(new Media(getVlc(), uri), options);
    }


    /**
     * Skip some number of millis; may be negative
     */
    public void skip(long skipMillis) {
        MediaPlayer player = this.player;
        if (player != null) {
            long newTime = Math.min(
                player.getLength(),
                Math.max(
                    0,
                    player.getTime() + skipMillis)
            );
            seekTo(newTime);
        }
    }

    /**
     * Jump to a specific time in millis
     */
    public void seekTo(long timeInMillis) {
        MediaPlayer player = this.player;
        if (player != null) {
            if (state != STATE_SEEK) {
                state = STATE_SEEK;
                notifyLoading(true);
            }
            player.setTime(timeInMillis);
        }
    }

    public void stop() {
        MediaPlayer player = this.player;
        if (player != null) {
            player.stop();
        }
    }

    /**
     * Stop and clean up; normally called for you when
     * the view is detached from the Window
     */
    public void release() {
        stop();

        MediaPlayer player = this.player;
        if (player != null && !player.isReleased()) {
            player.release();
        }

        this.player = null;
        state = STATE_IDLE;
    }

    void onNewLayout(IVLCVout vlcVout,
            int width, int height,
            int visibleWidth, int visibleHeight,
            int specifiedAspectNumerator,
            int specifiedAspectDenominator) {
        View decorView = getDecorView();
        int screenWidth = decorView.getWidth();
        int screenHeight = decorView.getHeight();

        // sanity check
        if (screenWidth * screenHeight == 0) {
            Log.e(TAG, "Unexpected screen size: " + screenWidth + "x" + screenHeight);
            return;
        }

        vlcVout.setWindowSize(screenWidth, screenHeight);

        if (width * height == 0) {
            // vlc is handling it internally; we do nothing
            ViewGroup.LayoutParams playerParams = playerView.getLayoutParams();
            playerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            playerParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            playerView.setLayoutParams(playerParams);

            ViewGroup.LayoutParams containerParams = surfaceContainer.getLayoutParams();
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            surfaceContainer.setLayoutParams(containerParams);
            return;
        }

        // okay, we're in charge
        // compute the aspect ratio
        float videoWidth;
        if (specifiedAspectNumerator == specifiedAspectDenominator) {
            // no indication about the density, assuming 1:1
            videoWidth = visibleWidth;
        } else {
            // use the specified aspect ratio
            videoWidth = visibleWidth *
                 specifiedAspectNumerator /
                    (float) specifiedAspectDenominator;
        }
        float aspect = videoWidth / (float) visibleHeight;

        // compute display aspect ratio
        float floatScreenWidth = screenWidth;
        float floatScreenHeight = screenHeight;
        float displayAspect = floatScreenWidth / floatScreenHeight;
        if (displayAspect < aspect) {
            floatScreenHeight = floatScreenWidth / aspect;
        } else {
            floatScreenWidth = floatScreenHeight * aspect;
        }

        // set display size
        ViewGroup.LayoutParams playerParams = playerView.getLayoutParams();
        playerParams.width = (int) Math.ceil(floatScreenWidth * width / (float) visibleWidth);
        playerParams.height = (int) Math.ceil(floatScreenHeight * height / (float) visibleHeight);
        playerView.setLayoutParams(playerParams);
        subtitlesView.setLayoutParams(playerParams);

        // set frame size (crop if necessary)
        ViewGroup.LayoutParams surfaceParams = surfaceContainer.getLayoutParams();
        surfaceParams.width = (int) Math.floor(floatScreenWidth);
        surfaceParams.height = (int) Math.floor(floatScreenHeight);
        surfaceContainer.setLayoutParams(surfaceParams);
    }

    private void setMedia(@NonNull final Media media, List<String> options) {
        // release any existing player
        release();

        for (String opt : options) {
            media.addOption(opt);
        }

        state = STATE_LOADING;

        player = new MediaPlayer(getVlc());
        player.setMedia(media);
        player.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                final VlcVideoView thisView = VlcVideoView.this;
                switch (event.type) {
                case MediaPlayer.Event.EndReached:
                    final OnCompletionListener onCompletion = thisView.onCompletion;
                    if (onCompletion != null) {
                        onCompletion.onCompletion(thisView);
                    }
                    break;

                case MediaPlayer.Event.Buffering:
                    if (state == STATE_PLAYBACK) {
                        state = STATE_BUFFER;
                        notifyLoading(true);
                    }
                    break;

                case MediaPlayer.Event.EncounteredError:
                    final OnErrorListener onError = thisView.onError;
                    if (onError != null) {
                        onError.onError(thisView);
                    } else {
                        Log.w(TAG, "Encountered Error!");
                    }
                    break;

                case MediaPlayer.Event.TimeChanged:
                    // NOTE: There's no explicit "prepared" event, and
                    //  sometimes Playing gets emitted before it actually
                    //  is (see the BigBuckBunny video). So, our little hack
                    //  is to use the first TimeChanged event
                    if (state == STATE_LOADING) {
                        // wait until they want to play
                        thisView.pause();
                    }

                    if (state < STATE_PLAYBACK) {
                        state = STATE_PLAYBACK;

                        final OnPreparedListener onPrepared = thisView.onPrepared;
                        if (onPrepared != null) {
                            onPrepared.onPrepared(thisView);
                        } else {
                            Log.v(TAG, "onPrepared");
                        }
                    } else if (state == STATE_BUFFER) {
                        state = STATE_PLAYBACK;
                        removeCallbacks(dispatchNotLoadingRunnable);
                        postDelayed(dispatchNotLoadingRunnable, 150);
                    } else if (state == STATE_SEEK) {
                        // this event was just a direct result of the seek;
                        //  now we buffer
                        state = STATE_BUFFER;
                    }
                    break;
                }
            }
        });

        IVLCVout vlcOut = player.getVLCVout();
        if (vlcOut.areViewsAttached()) {
            player.stop();
            vlcOut.detachViews();
        }

        vlcOut.addCallback(vlcOutCallback);

        vlcOut.setVideoView(playerView);
        vlcOut.setSubtitlesView(subtitlesView);
        vlcOut.attachViews();

        player.setVideoTrackEnabled(true);
        player.play();
    }


    private @Nullable Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }

            context = ((ContextWrapper) context).getBaseContext();
        }

        return null;
    }

    private View getDecorView() {
        Activity act = getActivity();
        if (act != null) {
            return act.getWindow().getDecorView();
        }

        throw new IllegalStateException("Couldn't get DecorView");
    }

    LibVLC getVlc() {
        LibVLC existing = vlcInstance;
        if (existing != null) return existing;

        // should we cache this?
        return vlcInstance = new LibVLC(
            getContext().getApplicationContext(),
            VlcOptions.get());
    }

    void notifyLoading(boolean isLoading) {
        removeCallbacks(dispatchNotLoadingRunnable);
        if (onLoadingStateChangedListener != null) {
            onLoadingStateChangedListener.onLoadingStateChanged(this, isLoading);
        }
    }
}

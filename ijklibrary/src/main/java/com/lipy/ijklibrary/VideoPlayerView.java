package com.lipy.ijklibrary;


import com.lipy.ijklibrary.listener.FrameImageLoadListener;
import com.lipy.ijklibrary.listener.ImageLoaderListener;
import com.lipy.ijklibrary.listener.VideoPlayerListener;
import com.lipy.ijklibrary.utils.OrientationUtils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.lang.reflect.Constructor;

import tv.danmaku.ijk.media.exo.IjkExoMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;


/**
 * 视频播放view
 * Created by lipy on 2017/3/12.
 */

public class VideoPlayerView extends RelativeLayout implements VideoPlayerManagerListener, View.OnClickListener, TextureView.SurfaceTextureListener {


    private static final String TAG = "VideoPlayerView";
    private static final int TIME_MSG = 0x01;
    private static final int TIME_INVAL = 1000;
    private static final int STATE_ERROR = -1;//错误
    private static final int STATE_IDLE = 0;//闲置
    private static final int STATE_PLAYING = 1;//播放中
    private static final int STATE_PAUSING = 2;//暂停
    private static final int LOAD_TOTAL_COUNT = 3;//重试次数


    private String mUrl;//视频地址
    private String mFrameURI;
    private int mScreenWidth, mDestationHeight;
    public static float VIDEO_HEIGHT_PERCENT = 9 / 16.0f;//视频宽高比
    public static int VIDEO_SCREEN_PERCENT = 50;    //自动播放阈值
    protected boolean mIfCurrentIsFullscreen = false;//当前是否全屏

    private boolean canPlay = true;//是否
    private boolean mIsRealPause;//是否真正暂停
    private boolean mIsComplete;//是否播放完成
    private int mCurrentCount;//当前重试次数
    private int playerState = STATE_IDLE;//播放状态，默认闲置


    private View view;
    private ViewGroup mViewGroup;
    private AudioManager mAudioManager;
    private VideoTexture mVideoView;
    private Button mMiniPlayBtn;
    private ImageView mFullBtn;
    private ImageView mLoadingBar;
    private ImageView mFrameView;
    private Surface videoSurface;
    protected OrientationUtils mOrientationUtils; //旋转工具类
    private VideoPlayerListener listener;
    private FrameImageLoadListener mFrameLoadListener;
    private ScreenEventReceiver mScreenReceiver;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TIME_MSG:
                    if (VideoPlayerManager.getInstance(getContext()).isPlaying()) {
                        if (listener != null) {
                            listener.onBufferUpdate(VideoPlayerManager.getInstance(getContext()).getCurrentPosition());
                        }
                        sendEmptyMessageDelayed(TIME_MSG, TIME_INVAL);//
                    }
                    break;
            }


        }
    };
    private RelativeLayout mTextureViewGroup;
    private IjkExoMediaPlayer mMediaPlayer;
    private VideoPlayerManager mPlayerManager;

    public VideoPlayerView(Context context, Boolean fullFlag) {
        super(context);
        mIfCurrentIsFullscreen = fullFlag;
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        initConfig();
        initView();
        registerBroadcastReceiver();

    }

    public void setViewGroup(ViewGroup viewGroup) {
        mViewGroup = viewGroup;
    }

    public VideoPlayerView(Context context, ViewGroup viewGroup) {
        super(context);
        mViewGroup = viewGroup;
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        initConfig();
        initView();
        registerBroadcastReceiver();
    }

    private void initConfig() {
        mPlayerManager = VideoPlayerManager.getInstance(getContext());
        mMediaPlayer = mPlayerManager.getMediaPlayer();
        mPlayerManager.setManagerListener(this);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        mScreenWidth = displayMetrics.widthPixels;
        mDestationHeight = (int) (mScreenWidth * VIDEO_HEIGHT_PERCENT);
    }

    private void initView() {
        view = LayoutInflater.from(getContext()).inflate(R.layout.xadsdk_video_player, this);
        mVideoView = new VideoTexture(getContext());
        mTextureViewGroup = (RelativeLayout) view.findViewById(R.id.textureViewGroup);
        mTextureViewGroup.addView(mVideoView);
        mVideoView.setOnClickListener(this);
        mVideoView.setKeepScreenOn(true);
        mVideoView.setSurfaceTextureListener(this);
        initSmallLayoutMode();
    }

    // 小模式状态
    private void initSmallLayoutMode() {
        LayoutParams params = new LayoutParams(mScreenWidth, mDestationHeight);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        view.setLayoutParams(params);
        mMiniPlayBtn = (Button) view.findViewById(R.id.xadsdk_small_play_btn);
        mFullBtn = (ImageView) view.findViewById(R.id.xadsdk_to_full_view);
        mLoadingBar = (ImageView) view.findViewById(R.id.loading_bar);
        mFrameView = (ImageView) view.findViewById(R.id.framing_view);
        mMiniPlayBtn.setOnClickListener(this);
        mFullBtn.setOnClickListener(this);
        showPauseView(false);
    }

    public void setDataUrl(String url) {
        this.mUrl = url;
    }

    public void isShowFullBtn(boolean isShow) {
        mFullBtn.setImageResource(isShow ? R.drawable.xadsdk_ad_mini : R.drawable.xadsdk_ad_mini_null);
        mFullBtn.setVisibility(isShow ? View.VISIBLE : View.GONE);
    }

    public boolean isRealPause() {
        return mIsRealPause;
    }

    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * 进入播放状态时的状态更新
     */
    private void entryResumeState() {
        canPlay = true;
        setCurrentPlayState(STATE_PLAYING);
        setIsRealPause(false);
        setIsComplete(false);
    }

    public void setIsComplete(boolean isComplete) {
        mIsComplete = isComplete;
    }

    //播放完成 ,变量标志完成
    public void setIsRealPause(boolean isRealPause) {
        this.mIsRealPause = isRealPause;
    }

    private void setCurrentPlayState(int state) {
        playerState = state;
    }


    public void setListener(VideoPlayerListener listener) {
        this.listener = listener;
    }

    public void setFrameLoadListener(FrameImageLoadListener frameLoadListener) {
        this.mFrameLoadListener = frameLoadListener;
    }

    private void showPauseView(boolean show) {
        mFullBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        mMiniPlayBtn.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
        if (!show) {
            mFrameView.setVisibility(View.VISIBLE);
            loadFrameImage();
        } else {
            mFrameView.setVisibility(View.GONE);
        }
    }

    private void showLoadingView() {
        mFullBtn.setVisibility(View.GONE);
        mLoadingBar.setVisibility(View.VISIBLE);
        AnimationDrawable anim = (AnimationDrawable) mLoadingBar.getBackground();
        anim.start();
        mMiniPlayBtn.setVisibility(View.GONE);
        mFrameView.setVisibility(View.GONE);
        loadFrameImage();
    }

    private void showPlayView() {
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
        mMiniPlayBtn.setVisibility(View.GONE);
        mFrameView.setVisibility(View.GONE);
    }


    @Override
    public boolean onInfo(int what, int extra) {
        return false;
    }

    @Override
    public void onBufferingUpdate(int i) {

    }

    @Override
    public void onVideoSizeChanged() {

    }

    @Override
    public void onSeekComplete() {

    }

    //加载视频url
    public void load() {
        if (playerState != STATE_IDLE) {
            return;
        }
        Log.e(TAG, "do play url = " + this.mUrl);
        showLoadingView();
        try {
            setCurrentPlayState(STATE_IDLE);
            mPlayerManager.setMute(true);
            mMediaPlayer.setDataSource(this.mUrl);
            mMediaPlayer.prepareAsync(); //开始异步加载
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            stop(); //error以后重新调用stop加载
        }
    }

    //暂停
    public void pause() {

        if (this.playerState != STATE_PLAYING) {
            return;
        }
        Log.e(TAG, "do pause");
        setCurrentPlayState(STATE_PAUSING);
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            if (!this.canPlay) {
                this.mMediaPlayer.seekTo(0);
            }
        }
        this.showPauseView(false);
        mHandler.removeCallbacksAndMessages(null);
    }

    //恢复播放
    public void resume() {

        if (playerState != STATE_PAUSING) {
            return;
        }
        Log.e(TAG, "do resume");
        if (!mMediaPlayer.isPlaying()) {
            entryResumeState();
            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.start();
            mHandler.sendEmptyMessage(TIME_MSG);
            showPauseView(true);
        } else {
            showPauseView(false);
        }

        if (playerState != STATE_PAUSING) {
            return;
        }
    }

    //视频停止
    public void stop() {
        Log.e(TAG, " do stop");
        if (this.mMediaPlayer != null) {
            this.mMediaPlayer.reset();
            this.mMediaPlayer.setOnSeekCompleteListener(null);
            this.mMediaPlayer.stop();
            this.mMediaPlayer.release();
            this.mMediaPlayer = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        setCurrentPlayState(STATE_IDLE);
        if (mCurrentCount < LOAD_TOTAL_COUNT) { //满足重新加载的条件
            mCurrentCount += 1;
            load();
        } else {
            showPauseView(false); //显示暂停状态
        }
    }

    //播放完成后回到初始状态
    public void playBack() {
        Log.e(TAG, " do playBack");
        setCurrentPlayState(STATE_PAUSING);
        mHandler.removeCallbacksAndMessages(null);
        if (mMediaPlayer != null) {
            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.seekTo(0);
            mMediaPlayer.pause();
        }
        this.showPauseView(false);
    }

    //销毁播放器
    public void destory() {
        Log.e(TAG, " do destroy");
        if (this.mMediaPlayer != null) {
            this.mMediaPlayer.setOnSeekCompleteListener(null);
            this.mMediaPlayer.stop();
            this.mMediaPlayer.release();
            this.mMediaPlayer = null;
        }
        mHandler.removeCallbacksAndMessages(null); //release all message and runnable
        unRegisterBroadcastReceiver();
        setCurrentPlayState(STATE_IDLE);
        mCurrentCount = 0;
        setIsComplete(false);
        setIsRealPause(false);
        showPauseView(false); //除了播放和loading外其余任何状态都显示pause
    }

    //跳到指定点播放视频
    public void seekAndResume(int position) {

        showPauseView(true);
        entryResumeState();
        VideoPlayerManager.getInstance(getContext()).getMediaPlayer().seekTo(position);
        VideoPlayerManager.getInstance(getContext()).getMediaPlayer().setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(IMediaPlayer iMediaPlayer) {
                Log.d(TAG, "do seek and resume");
                iMediaPlayer.start();
                mHandler.sendEmptyMessage(TIME_MSG);
            }
        });

    }

    //跳到指定点暂停视频
    public void seekAndPause(int position) {
        if (this.playerState != STATE_PLAYING) {
            return;
        }
        showPauseView(false);
        setCurrentPlayState(STATE_PAUSING);
        if (VideoPlayerManager.getInstance(getContext()).isPlaying()) {
            VideoPlayerManager.getInstance(getContext()).getMediaPlayer().seekTo(position);
            VideoPlayerManager.getInstance(getContext()).getMediaPlayer().setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(IMediaPlayer iMediaPlayer) {
                    Log.e(TAG, "do seek and pause");
                    iMediaPlayer.pause();
                    mHandler.removeCallbacksAndMessages(null);
                }

            });
        }
    }

    /**
     * view显示改变时
     */
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        Log.e(TAG, "onVisibilityChanged");
//        if (visibility == VISIBLE && playerState == STATE_PAUSING) {
//            if (isRealPause() || isComplete()) {
//                pause();
//            } else {
//                decideCanPlay();
//            }
//        } else {
//            pause();
//        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == mMiniPlayBtn) {
            if (playerState == STATE_PAUSING) {
                if (getVisiblePercent(mViewGroup)
                        > VIDEO_SCREEN_PERCENT) {
                    resume();
                }
            } else {
                load();
            }
        } else if (v == mVideoView) {
            if (listener != null) {
                listener.onClickPlay();
            }
        } else if (v == mFullBtn) {
//            mSeekOnStart = VideoPlayerManager.getInstance(getContext()).getCurrentPosition();
            startWindowFullscreen(getContext(), true, true);
            if (listener != null) {
                listener.onClickFullScreenBtn();
            }
        }


    }

    /**
     * TextureView就绪
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (VideoPlayerManager.getInstance(getContext()).isPlaying()) {
            showPlayView();
        }
        Log.e(TAG, "onSurfaceTextureAvailable");
        videoSurface = new Surface(surface);
        VideoPlayerManager.getInstance(getContext()).showDisplay(videoSurface);

    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        VideoPlayerManager.getInstance(getContext()).showDisplay(null);
        surface.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//        Log.e(TAG, "onSurfaceTextureUpdated");
    }


    protected long mSeekOnStart = -1; //从哪个开始播放


    @Override
    public void onCompletion() {
        Log.e(TAG, "onCompletion");
        if (listener != null) {
            listener.onAdVideoLoadComplete();
        }
        setIsComplete(true);
        setIsRealPause(true);
        playBack();
    }

    /**
     * 播放器播放异常
     */
    @Override
    public void onError(int i, int i1) {
        Log.e(TAG, "onError");
        setCurrentPlayState(STATE_ERROR);
        if (mCurrentCount >= LOAD_TOTAL_COUNT) {
            if (listener != null) {
                listener.onAdVideoLoadFailed();
            }
            showPauseView(false);
        }
        load();
    }

    /**
     * 播放器就绪状态
     */
    @Override
    public void onPrepared() {
        showPauseView(true);
        entryResumeState();
    }

    public void setId() {
        setId(FULLSCREEN_ID);
    }

    /**
     * 监听锁屏事件的广播接收器
     */
    private class ScreenEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //主动锁屏时 pause, 主动解锁屏幕时，resume
            switch (intent.getAction()) {
                case Intent.ACTION_USER_PRESENT:
                    Log.e(TAG, "playerState:STATE_PAUSING" + "--" + STATE_PLAYING);
                    if (playerState == STATE_PAUSING) {
                        if (isIfCurrentIsFullscreen()) {
                            setCurrentPlayState(STATE_PAUSING);
                            resume();
                        } else {
                            if (mIsRealPause) {
                                //手动点的暂停，回来后还暂停
                                pause();
                            } else {
                                decideCanPlay();
                            }
                        }
                    }

                    break;
                case Intent.ACTION_SCREEN_OFF:
                    Log.e(TAG, "playerState:STATE_PLAYING" + "--" + STATE_PLAYING);
                    if (playerState == STATE_PLAYING) {
                        pause();
                    }
                    break;
            }
        }

    }

    private void registerBroadcastReceiver() {
        if (mScreenReceiver == null) {
            mScreenReceiver = new ScreenEventReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            getContext().registerReceiver(mScreenReceiver, filter);
        }
    }

    private void unRegisterBroadcastReceiver() {
        if (mScreenReceiver != null) {
            getContext().unregisterReceiver(mScreenReceiver);
        }
    }

    private void decideCanPlay() {
        if (getVisiblePercent(mViewGroup) > VIDEO_SCREEN_PERCENT) {
            //来回切换页面时，只有 >50,且满足自动播放条件才自动播放
            setCurrentPlayState(STATE_PAUSING);
            resume();
        } else {
            setCurrentPlayState(STATE_PLAYING);
            pause();
        }
    }

    /**
     * 异步加载定帧图
     */
    private void loadFrameImage() {
        if (mFrameLoadListener != null) {
            mFrameLoadListener.onStartFrameLoad(mFrameURI, new ImageLoaderListener() {
                @Override
                public void onLoadingComplete(Bitmap loadedImage) {
                    if (loadedImage != null) {
                        mFrameView.setScaleType(ImageView.ScaleType.FIT_XY);
                        mFrameView.setImageBitmap(loadedImage);
                    } else {
                        mFrameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        mFrameView.setImageResource(R.drawable.xadsdk_img_error);
                    }
                }
            });
        }
    }

    private Handler mHandler = new Handler();

    protected boolean mHideKey = true;//是否隐藏虚拟按键

    public boolean isHideKey() {
        return mHideKey;
    }

    /**
     * 全屏隐藏虚拟按键，默认打开
     */
    public void setHideKey(boolean hideKey) {
        this.mHideKey = hideKey;
    }

    private void resolveFullVideoShow(Context context, final VideoPlayerView videoPlayer, final RelativeLayout frameLayout) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) videoPlayer.getLayoutParams();
        lp.setMargins(0, 0, 0, 0);
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        videoPlayer.setLayoutParams(lp);
        videoPlayer.setIfCurrentIsFullscreen(true);
        mOrientationUtils = new OrientationUtils((Activity) context, videoPlayer);
        mOrientationUtils.setEnable(mRotateViewAuto);
        videoPlayer.mOrientationUtils = mOrientationUtils;

        mOrientationUtils.resolveByClick();//直接横屏

        if (mLockLand) {
            mOrientationUtils.resolveByClick();
        }
        Log.e(TAG, "showFull");
        videoPlayer.setVisibility(VISIBLE);
        frameLayout.setVisibility(VISIBLE);


//        if (mVideoAllCallBack != null) {
//            Debuger.printfError("onEnterFullscreen");
//            mVideoAllCallBack.onEnterFullscreen(mUrl, mObjects);
//        }
        mIfCurrentIsFullscreen = true;
    }

    protected boolean mLockLand = false;//当前全屏是否锁定全屏

    public boolean isLockLand() {
        return mLockLand;
    }

    /**
     * 一全屏就锁屏横屏，默认false竖屏，可配合setRotateViewAuto使用
     */
    public void setLockLand(boolean lockLand) {
        this.mLockLand = lockLand;
    }


    protected boolean mRotateViewAuto = true; //是否自动旋转

    public boolean isRotateViewAuto() {
        return mRotateViewAuto;
    }

    /**
     * 是否开启自动旋转
     */
    public void setRotateViewAuto(boolean rotateViewAuto) {
        this.mRotateViewAuto = rotateViewAuto;
        if (mOrientationUtils != null) {
            mOrientationUtils.setEnable(rotateViewAuto);
        }
    }


    protected boolean mActionBar = true;//是否需要在利用window实现全屏幕的时候隐藏actionbar

    protected boolean mStatusBar = true;//是否需要在利用window实现全屏幕的时候隐藏statusbar

    protected int[] mListItemRect;//当前item框的屏幕位置

    protected int[] mListItemSize;//当前item的大小

    /**
     * 保存大小和状态
     */
    private void saveLocationStatus(Context context, boolean statusBar, boolean actionBar) {
        getLocationOnScreen(mListItemRect);
        int statusBarH = getStatusBarHeight(context);
        int actionBerH = getActionBarHeight((Activity) context);
        if (statusBar) {
            mListItemRect[1] = mListItemRect[1] - statusBarH;
        }
        if (actionBar) {
            mListItemRect[1] = mListItemRect[1] - actionBerH;
        }
        mListItemSize[0] = getWidth();
        mListItemSize[1] = getHeight();
    }

    /**
     * 获取状态栏高度
     *
     * @param context 上下文
     * @return 状态栏高度
     */
    public static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * 获取ActionBar高度
     *
     * @param activity activity
     * @return ActionBar高度
     */
    public static int getActionBarHeight(Activity activity) {
        TypedValue tv = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
        }
        return 0;
    }

    private ViewGroup getViewGroup() {
        return (ViewGroup) (scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
    }

    /**
     * Get activity from context object
     *
     * @param context something
     * @return object of Activity or null if it is not Activity
     */
    public static Activity scanForActivity(Context context) {
        if (context == null) return null;

        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) context).getBaseContext());
        }

        return null;
    }

    /**
     * 移除没用的
     */
    private void removeVideo(ViewGroup vp, int id) {
        Log.e(TAG, "removeView:" + vp.getChildCount() + "---" + id);
        View old = vp.findViewById(id);
        if (old != null) {
            if (old.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) old.getParent();
                viewGroup.removeView(old);
            }
        }
    }

    private static final int FULLSCREEN_ID = 0x0123;

    /**
     * 利用window层播放全屏效果
     *
     * @param actionBar 是否有actionBar，有的话需要隐藏
     * @param statusBar 是否有状态bar，有的话需要隐藏
     */
    public VideoPlayerView startWindowFullscreen(final Context context, final boolean actionBar, final boolean statusBar) {

        hideSupportActionBar(context, actionBar, statusBar);

        if (mHideKey) {
            hideNavKey(context);
        }

        this.mActionBar = actionBar;

        this.mStatusBar = statusBar;

        mListItemRect = new int[2];

        mListItemSize = new int[2];

        final ViewGroup vp = getViewGroup();

        removeVideo(vp, FULLSCREEN_ID);

        if (mTextureViewGroup.getChildCount() > 0) {
            mTextureViewGroup.removeAllViews();
        }

        saveLocationStatus(context, statusBar, actionBar);

        boolean hadNewConstructor = true;
        try {
            VideoPlayerView.this.getClass().getConstructor(Context.class, Boolean.class);
        } catch (Exception e) {
            hadNewConstructor = false;
        }

        try {
            //通过被重载的不同构造器来选择
            Constructor<VideoPlayerView> constructor;
            final VideoPlayerView videoPlayerView;
            if (!hadNewConstructor) {
                constructor = (Constructor<VideoPlayerView>) VideoPlayerView.this.getClass().getConstructor(Context.class);
                videoPlayerView = constructor.newInstance(getContext());
            } else {
                constructor = (Constructor<VideoPlayerView>) VideoPlayerView.this.getClass().getConstructor(Context.class, Boolean.class);
                videoPlayerView = constructor.newInstance(getContext(), true);
            }
            videoPlayerView.setId(FULLSCREEN_ID);
            videoPlayerView.setIfCurrentIsFullscreen(true);
            RelativeLayout.LayoutParams lpParent = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            RelativeLayout relativeLayout = new RelativeLayout(context);
            relativeLayout.setBackgroundColor(Color.BLACK);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(getWidth(), getHeight());
            videoPlayerView.setViewGroup(relativeLayout);
            relativeLayout.addView(videoPlayerView, lp);
            vp.addView(relativeLayout, lpParent);
            videoPlayerView.setVisibility(INVISIBLE);
            relativeLayout.setVisibility(INVISIBLE);
            resolveFullVideoShow(context, videoPlayerView, relativeLayout);

//            videoPlayerView.setDataUrl(mUrl);
            videoPlayerView.addTextureView();
            VideoPlayerManager.getInstance(getContext()).setManagerListener(videoPlayerView);
            return videoPlayerView;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected int mRotate = 0; //针对某些视频的旋转信息做了旋转处理

    /**
     * 添加播放的view
     */
    protected void addTextureView() {
        Log.e(TAG, "addTextureView:" + mTextureViewGroup);
        if (mTextureViewGroup.getChildCount() > 0) {
            mTextureViewGroup.removeAllViews();
        }
        mVideoView = null;
        mVideoView = new VideoTexture(getContext());
        mVideoView.setSurfaceTextureListener(this);
        mVideoView.setRotation(mRotate);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mTextureViewGroup.addView(mVideoView, layoutParams);
    }


    public static void hideSupportActionBar(Context context, boolean actionBar, boolean statusBar) {
        if (actionBar) {
            AppCompatActivity appCompatActivity = getAppCompActivity(context);
            if (appCompatActivity != null) {
                ActionBar ab = appCompatActivity.getSupportActionBar();
                if (ab != null) {
                    ab.setShowHideAnimationEnabled(false);
                    ab.hide();
                }
            }
        }
        if (statusBar) {
            if (context instanceof FragmentActivity) {
                FragmentActivity fragmentActivity = (FragmentActivity) context;
                fragmentActivity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                getAppCompActivity(context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    /**
     * Get AppCompatActivity from context
     *
     * @return AppCompatActivity if it's not null
     */
    public static AppCompatActivity getAppCompActivity(Context context) {
        if (context == null) return null;
        if (context instanceof AppCompatActivity) {
            return (AppCompatActivity) context;
        } else if (context instanceof ContextThemeWrapper) {
            return getAppCompActivity(((ContextThemeWrapper) context).getBaseContext());
        }
        return null;
    }


    public static void hideNavKey(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            //       设置屏幕始终在前面，不然点击鼠标，重新出现虚拟按键
            ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav
                            // bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE);
        } else {
            ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav
            );
        }
    }


    //获取屏幕当前展示的百分比
    public static int getVisiblePercent(View pView) {
        if (pView != null && pView.isShown()) {
            DisplayMetrics displayMetrics = pView.getContext().getResources().getDisplayMetrics();
            int displayWidth = displayMetrics.widthPixels;
            Rect rect = new Rect();
            pView.getGlobalVisibleRect(rect);
            if ((rect.top > 0) && (rect.left < displayWidth)) {
                double areaVisible = rect.width() * rect.height();
                double areaTotal = pView.getWidth() * pView.getHeight();
                return (int) ((areaVisible / areaTotal) * 100);
            } else {
                return -1;
            }
        }
        return -1;
    }

    public boolean isIfCurrentIsFullscreen() {
        return mIfCurrentIsFullscreen;
    }

    public void setIfCurrentIsFullscreen(boolean ifCurrentIsFullscreen) {
        this.mIfCurrentIsFullscreen = ifCurrentIsFullscreen;
    }

//    public static boolean canAutoPlay(Context context, AutoPlaySetting setting) {
//        boolean result = true;
//        switch (setting) {
//            case AUTO_PLAY_3G_4G_WIFI:
//                result = true;
//                break;
//            case AUTO_PLAY_ONLY_WIFI:
//                if (isWifiConnected(context)) {
//                    result = true;
//                } else {
//                    result = false;
//                }
//                break;
//            case AUTO_PLAY_NEVER:
//                result = false;
//                break;
//        }
//        return result;
//    }


    public static String getNetworkTypeWIFI2G3G(Context context) {
        String strNetworkType = "";

        try {
            ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    strNetworkType = "WIFI";
                } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    String _strSubTypeName = networkInfo.getSubtypeName();

                    // TD-SCDMA networkType is 17
                    int networkType = networkInfo.getSubtype();
                    switch (networkType) {
                        case TelephonyManager.NETWORK_TYPE_GPRS:
                        case TelephonyManager.NETWORK_TYPE_EDGE:
                        case TelephonyManager.NETWORK_TYPE_CDMA:
                        case TelephonyManager.NETWORK_TYPE_1xRTT:
                        case TelephonyManager.NETWORK_TYPE_IDEN: // api<8 : replace by
                            // 11
                            strNetworkType = "2G";
                            break;
                        case TelephonyManager.NETWORK_TYPE_UMTS:
                        case TelephonyManager.NETWORK_TYPE_EVDO_0:
                        case TelephonyManager.NETWORK_TYPE_EVDO_A:
                        case TelephonyManager.NETWORK_TYPE_HSDPA:
                        case TelephonyManager.NETWORK_TYPE_HSUPA:
                        case TelephonyManager.NETWORK_TYPE_HSPA:
                        case TelephonyManager.NETWORK_TYPE_EVDO_B: // api<9 : replace by
                            // 14
                        case TelephonyManager.NETWORK_TYPE_EHRPD: // api<11 : replace by
                            // 12
                        case TelephonyManager.NETWORK_TYPE_HSPAP: // api<13 : replace by
                            // 15
                            strNetworkType = "3G";
                            break;
                        case TelephonyManager.NETWORK_TYPE_LTE: // api<11 : replace by
                            // 13
                            strNetworkType = "4G";
                            break;
                        default:
                            // TD-SCDMA 中国移动 联通 电信 三种3G制式
                            if (_strSubTypeName.equalsIgnoreCase("TD-SCDMA")
                                    || _strSubTypeName.equalsIgnoreCase("WCDMA")
                                    || _strSubTypeName.equalsIgnoreCase("CDMA2000")) {
                                strNetworkType = "3G";
                            } else {
                                strNetworkType = _strSubTypeName;
                            }

                            break;
                    }

                    if (!TextUtils.isEmpty(_strSubTypeName)) {
                        strNetworkType += " " + _strSubTypeName;
                    }
                } else {
                    strNetworkType = "unknown";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return strNetworkType;
    }


}

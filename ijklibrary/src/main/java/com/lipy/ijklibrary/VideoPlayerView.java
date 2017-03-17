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
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 视频播放view
 * Created by lipy on 2017/3/12.
 */

public class VideoPlayerView extends RelativeLayout implements IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnCompletionListener, IMediaPlayer.OnErrorListener,
        View.OnClickListener, TextureView.SurfaceTextureListener, IMediaPlayer.OnInfoListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnVideoSizeChangedListener {


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
    private boolean isMute;//是否静音
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
    private GSYTextureView mVideoView;
    private Button mMiniPlayBtn;
    private ImageView mFullBtn;
    private ImageView mLoadingBar;
    private ImageView mFrameView;
    private Surface videoSurface;
    protected OrientationUtils mOrientationUtils; //旋转工具类
    private IjkMediaPlayer mediaPlayer;
    private VideoPlayerListener listener;
    private FrameImageLoadListener mFrameLoadListener;
    private ScreenEventReceiver mScreenReceiver;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TIME_MSG:
                    if (isPlaying()) {
                        if (listener != null) {
                            listener.onBufferUpdate(getCurrentPosition());
                        }
                        sendEmptyMessageDelayed(TIME_MSG, TIME_INVAL);//
                    }
                    break;
            }


        }
    };
    public static int mCurrentVideoWidth = 0;
    public static int mCurrentVideoHeight = 0;
    private RelativeLayout mTextureViewGroup;

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
        Log.e(TAG, "setViewGroup:" + mViewGroup);
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
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        mScreenWidth = displayMetrics.widthPixels;
        mDestationHeight = (int) (mScreenWidth * VIDEO_HEIGHT_PERCENT);
    }

    private void initView() {
        view = LayoutInflater.from(getContext()).inflate(R.layout.xadsdk_video_player, this);
        mVideoView = new GSYTextureView(getContext());
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

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
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

    private synchronized void checkMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = createMediaPlayer(); //每次都重新创建一个新的播放器
        }
    }

    private IjkMediaPlayer createMediaPlayer() {
        mediaPlayer = new IjkMediaPlayer();
        mediaPlayer.reset();
        mediaPlayer.setOnCompletionListener(this);
//        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setScreenOnWhilePlaying(true);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnVideoSizeChangedListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);

        if (videoSurface != null && videoSurface.isValid()) {
            mediaPlayer.setSurface(videoSurface);
        } else {
            stop();
        }
        return mediaPlayer;
    }

    private void initEXOPlayer() {

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

    public int getCurrentPosition() {
        if (this.mediaPlayer != null) {
            return (int) mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            return (int) mediaPlayer.getDuration();
        }
        return 0;
    }


    //加载视频url
    public void load() {
        if (playerState != STATE_IDLE) {
            return;
        }
        try {
            Log.e(TAG, "load");
            showLoadingView();
            setCurrentPlayState(STATE_IDLE);
            checkMediaPlayer();
            mediaPlayer.setDataSource(mUrl);
            mediaPlayer.prepareAsync();//异步加载视频资源
        } catch (Exception e) {
            stop();
            e.printStackTrace();
        }
    }

    //暂停
    public void pause() {
        Log.e(TAG, "pause STATE_PLAYING");
        if (playerState != STATE_PLAYING) {
            return;
        }
        Log.e(TAG, "pause");
        setCurrentPlayState(STATE_PAUSING);
        if (isPlaying()) {
            mediaPlayer.pause();
        }
        showPauseView(false);
        handler.removeCallbacksAndMessages(null);

    }

    //恢复播放
    public void resume() {
        if (playerState != STATE_PAUSING) {
            return;
        }
        Log.e(TAG, "resume");
        Log.e(TAG, "resume isPlaying:" + isPlaying());
        if (!isPlaying()) {
            entryResumeState();
            showPauseView(true);
//            mediaPlayer.setOnSeekCompleteListener(null);
            mediaPlayer.start();
            handler.sendEmptyMessage(TIME_MSG);
        } else {
            showPauseView(false);
        }

    }

    //视频停止
    public void stop() {
        Log.e(TAG, "stop");
        if (this.mediaPlayer != null) {
            this.mediaPlayer.reset();
//            this.mediaPlayer.setOnSeekCompleteListener(null);
            this.mediaPlayer.stop();
            this.mediaPlayer.release();
            this.mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
        setCurrentPlayState(STATE_IDLE);
        //retry
        if (mCurrentCount < LOAD_TOTAL_COUNT) {
            mCurrentCount += 1;
            load();
        } else {
            showPauseView(false);//显示暂停状态
        }
    }

    //播放完成后回到初始状态
    public void playBack() {
        Log.e(TAG, "playBack");
        setCurrentPlayState(STATE_PAUSING);
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
//            mediaPlayer.setOnSeekCompleteListener(null);
            mediaPlayer.seekTo(0);
            mediaPlayer.pause();
        }
        showPauseView(false);
    }

    //销毁播放器
    public void destory() {
        Log.e(TAG, "destory");
        mHadPlay = true;
        unRegisterBroadcastReceiver();
    }


    /**
     * @param time
     */
    public void seekAndResume(int time) {
        Log.e(TAG, "seekAndResume");
    }

    /**
     * @param time
     */
    public void seekAndPause(int time) {
        Log.e(TAG, "seekAndPause");
    }


    /**
     * view显示改变时
     */
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        Log.e(TAG, "onVisibilityChanged");
        if (visibility == VISIBLE && playerState == STATE_PAUSING) {
            if (isRealPause() || isComplete()) {
                pause();
            } else {
                decideCanPlay();
            }
        } else {
            pause();
        }
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
        Log.e(TAG, "onSurfaceTextureAvailable");

        videoSurface = new Surface(surface);
        checkMediaPlayer();
        mediaPlayer.setSurface(videoSurface);
        load();
    }

    private void showDisplay(Surface surface) {

            if (mediaPlayer != null && surface.isValid()) {
                mediaPlayer.setSurface(surface);
            }
            if (mediaPlayer instanceof IjkMediaPlayer) {
                if (mediaPlayer != null && mediaPlayer.getDuration() > 30
                        && mediaPlayer.getCurrentPosition() < mediaPlayer.getDuration()) {
                    mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 20);
                }
            }
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//        Log.e(TAG, "onSurfaceTextureUpdated");
    }

    /**
     * 播放器缓冲更新
     */
    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {
        Log.e(TAG, "onBufferingUpdate");
        mBuffterPoint = i;
    }

    /**
     * 播放器播放完成
     */
    @Override
    public void onCompletion(IMediaPlayer iMediaPlayer) {
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
    public boolean onError(IMediaPlayer iMediaPlayer, int i, int i1) {
        Log.e(TAG, "onError");
        setCurrentPlayState(STATE_ERROR);
        if (mCurrentCount >= LOAD_TOTAL_COUNT) {
            if (listener != null) {
                listener.onAdVideoLoadFailed();
            }
            showPauseView(false);
        }
        stop();
        return true;//返回true 自己处理异常
    }

    /**
     * 播放器就绪状态
     */
    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        Log.e(TAG, "onPrepared");
//        if (isPlaying()) {
//            return;
//        }
        showPlayView();
        showPauseView(true);
        mediaPlayer = (IjkMediaPlayer) iMediaPlayer;
        if (mediaPlayer != null) {
            mediaPlayer.setOnBufferingUpdateListener(this);
            mCurrentCount = 0;
            if (listener != null) {
                listener.onAdVideoLoadSuccess();
            }
//            decideCanPlay();
        }
        mHadPlay = true;
    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, int i, int i1) {
        if (i1 == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            mRotate = i1;
        }
        return false;
    }

    @Override
    public void onSeekComplete(IMediaPlayer iMediaPlayer) {
        Log.e(TAG, "onSeekComplete");
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int i, int i1, int i2, int i3) {
        mCurrentVideoWidth = iMediaPlayer.getVideoWidth();
        mCurrentVideoHeight = iMediaPlayer.getVideoHeight();
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
                    Log.e(TAG, "playerState:STATE_PAUSING");
                    if (playerState == STATE_PAUSING) {
                        if (mIsRealPause) {
                            //手动点的暂停，回来后还暂停
                            pause();
                        } else {
                            decideCanPlay();
                        }
                    }
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    Log.e(TAG, "playerState:STATE_PLAYING");
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


    private int mSystemUiVisibility;

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
        View old = vp.findViewById(id);
        if (old != null) {
            if (old.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) old.getParent();
                vp.removeView(viewGroup);
            }
        }
    }

    protected boolean mHadPlay = false;//是否播放过

    protected static final int FULLSCREEN_ID = 85597;
    protected Bitmap mFullPauseBitmap = null;//暂停时的全屏图片；

    /**
     * 全屏的暂停返回的时候返回页面不黑色
     */
    private void pauseFullBackCoverLogic(VideoPlayerView gsyVideoPlayer) {
        //如果是暂停状态
        if (gsyVideoPlayer.playerState == STATE_PAUSING
                && gsyVideoPlayer.mVideoView != null) {
            //全屏的位图还在，说明没播放，直接用原来的
            if (gsyVideoPlayer.mFullPauseBitmap != null
                    && !gsyVideoPlayer.mFullPauseBitmap.isRecycled()) {
                mFullPauseBitmap = gsyVideoPlayer.mFullPauseBitmap;
            } else {
                //不在了说明已经播放过，还是暂停的话，我们拿回来就好
                try {
                    mFullPauseBitmap = mVideoView.getBitmap(mVideoView.getSizeW(), mVideoView.getSizeH());
                } catch (Exception e) {
                    e.printStackTrace();
                    mFullPauseBitmap = null;
                }
            }
        }
    }

    /**
     * 全屏的暂停的时候返回页面不黑色
     */
    private void pauseFullCoverLogic() {
        if (playerState == STATE_PAUSING && mVideoView != null
                && (mFullPauseBitmap == null || mFullPauseBitmap.isRecycled())) {
            try {
                mFullPauseBitmap = mVideoView.getBitmap(mVideoView.getSizeW(), mVideoView.getSizeH());
            } catch (Exception e) {
                e.printStackTrace();
                mFullPauseBitmap = null;
            }
        }
    }


    protected boolean mLooping = false;//循环

    public boolean isLooping() {
        return mLooping;
    }

    /**
     * 设置循环
     */
    public void setLooping(boolean looping) {
        this.mLooping = looping;
    }

    /**
     * 利用window层播放全屏效果
     *
     * @param actionBar 是否有actionBar，有的话需要隐藏
     * @param statusBar 是否有状态bar，有的话需要隐藏
     */
    public VideoPlayerView startWindowFullscreen(final Context context, final boolean actionBar, final boolean statusBar) {

        mSystemUiVisibility = ((Activity) context).getWindow().getDecorView().getSystemUiVisibility();

        hideSupportActionBar(context, actionBar, statusBar);

        if (mHideKey) {
            hideNavKey(context);
        }

        this.mActionBar = actionBar;

        this.mStatusBar = statusBar;

        mListItemRect = new int[2];

        mListItemSize = new int[2];

        final ViewGroup vp = getViewGroup();
        vp.removeAllViews();

//        removeVideo(vp, FULLSCREEN_ID);


        //处理暂停的逻辑
        pauseFullCoverLogic();

        if (mTextureViewGroup.getChildCount() > 0) {
            mTextureViewGroup.removeAllViews();
        }


        saveLocationStatus(context, statusBar, actionBar);

//        boolean hadNewConstructor = true;
//        try {
//            VideoPlayerView.this.getClass().getConstructor(Context.class, ViewGroup.class);
//        } catch (Exception e) {
//            hadNewConstructor = false;
//        }

        try {
            //通过被重载的不同构造器来选择
            Constructor<VideoPlayerView> constructor;
            final VideoPlayerView gsyVideoPlayer;
//            if (!hadNewConstructor) {
//                constructor = (Constructor<VideoPlayerView>) VideoPlayerView.this.getClass().getConstructor(Context.class);
//                gsyVideoPlayer = constructor.newInstance(getContext());
//            } else {
            constructor = (Constructor<VideoPlayerView>) VideoPlayerView.this.getClass().getConstructor(Context.class, Boolean.class);
            gsyVideoPlayer = constructor.newInstance(getContext(), true);
//            }
//            gsyVideoPlayer = new VideoPlayerView(getContext(), mViewGroup);
            gsyVideoPlayer.setId(FULLSCREEN_ID);
            gsyVideoPlayer.setIfCurrentIsFullscreen(true);
            gsyVideoPlayer.setLooping(isLooping());

//            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
//            int w = wm.getDefaultDisplay().getWidth();
//            int h = wm.getDefaultDisplay().getHeight();
//            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h, w);
//            lp.setMargins((w - h) / 2, -(w - h) / 2, 0, 0);
//            vp.addView(gsyVideoPlayer, lp);

            final RelativeLayout.LayoutParams lpParent = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            final RelativeLayout frameLayout = new RelativeLayout(context);
            frameLayout.setBackgroundColor(Color.BLACK);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(getWidth(), getHeight());
            frameLayout.addView(gsyVideoPlayer, lp);
            vp.addView(frameLayout, lpParent);
            gsyVideoPlayer.setVisibility(INVISIBLE);
            frameLayout.setVisibility(INVISIBLE);
            resolveFullVideoShow(context, gsyVideoPlayer, frameLayout);

            gsyVideoPlayer.mHadPlay = mHadPlay;
            gsyVideoPlayer.mFullPauseBitmap = mFullPauseBitmap;
            gsyVideoPlayer.setDataUrl(mUrl);
            Log.e(TAG, mUrl);
            gsyVideoPlayer.setStateAndUi(playerState);
            gsyVideoPlayer.addTextureView();
//
//            gsyVideoPlayer.getFullscreenButton().setImageResource(getShrinkImageRes());
//            gsyVideoPlayer.getFullscreenButton().setOnClickListener(new OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    clearFullscreenLayout();
//                }
//            });
//
//            gsyVideoPlayer.getBackButton().setVisibility(VISIBLE);
//            gsyVideoPlayer.getBackButton().setOnClickListener(new OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    clearFullscreenLayout();
//                }
//            });
//
//            GSYVideoManager.instance().setLastListener(this);
//            GSYVideoManager.instance().setListener(gsyVideoPlayer);
            return gsyVideoPlayer;
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
//        if (mViewGroup.getChildCount() > 0) {
//            mTextureViewGroup.removeAllViews();
//        }
        mVideoView = null;
        mVideoView = new GSYTextureView(getContext());
        mVideoView.setSurfaceTextureListener(this);
        mVideoView.setRotation(mRotate);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mTextureViewGroup.addView(mVideoView, layoutParams);
    }

    protected int mBuffterPoint;//缓存进度

    /**
     * 设置播放显示状态
     */
    protected void setStateAndUi(int state) {
        playerState = state;
        switch (playerState) {
            case STATE_IDLE:
                mBuffterPoint = 0;
                if (mAudioManager != null) {
                    mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
                }
                break;
//            case CURRENT_STATE_PREPAREING:
//                resetProgressAndTime();
//                break;
            case STATE_PLAYING:
                showPlayView();
                break;
            case STATE_PAUSING:
                showPauseView(true);
                break;
            case STATE_ERROR:
                showPauseView(true);
                break;
//            case CURRENT_STATE_AUTO_COMPLETE:
//                cancelProgressTimer();
//                mProgressBar.setProgress(100);
//                mCurrentTimeTextView.setText(mTotalTimeTextView.getText());
//                break;
        }
        Log.e(TAG, "setStateAndUi" + playerState);
    }


    /**
     * 监听是否有外部其他多媒体开始播放
     */
    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:

                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (isPlaying()) {
                        pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };


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


    //是否静音
    public void setNeedMute(boolean needMute) {
        this.isMute = needMute;
        if (mediaPlayer != null) {
            if (needMute) {
                mediaPlayer.setVolume(0, 0);
            } else {
                mediaPlayer.setVolume(1, 1);
            }
        }
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

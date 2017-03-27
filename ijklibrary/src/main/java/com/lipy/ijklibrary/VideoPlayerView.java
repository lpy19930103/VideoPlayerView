package com.lipy.ijklibrary;


import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.lipy.ijklibrary.listener.FrameImageLoadListener;
import com.lipy.ijklibrary.listener.ImageLoaderListener;
import com.lipy.ijklibrary.listener.VideoPlayerListener;
import com.lipy.ijklibrary.utils.OrientationUtils;
import com.lipy.ijklibrary.utils.VideoUtil;

import java.lang.reflect.Constructor;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static com.lipy.ijklibrary.utils.VideoUtil.hideNavKey;


/**
 * 视频播放view
 * Created by lipy on 2017/3/12.
 */

public class VideoPlayerView extends RelativeLayout implements VideoPlayerManagerListener, View.OnTouchListener, View.OnClickListener, TextureView.SurfaceTextureListener, SeekBar.OnSeekBarChangeListener {


    private static final String TAG = "VideoPlayerView";

    private static final int TIME_MSG = 0x01;

    private static final int PROGRESS_MSG = 0x02;

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

    protected boolean mIfCurrentIsSmallscreen = false;//当前是否全屏

    private boolean canPlay = true;//是否

    private boolean mIsRealPause;//是否真正暂停

    private boolean mIsComplete;//是否播放完成

    private int mCurrentCount;//当前重试次数

    private int playerState = STATE_IDLE;//播放状态，默认闲置

    protected ProgressBar mBottomProgressBar;

    private View view;
    private ViewGroup mViewGroup;

    private VideoTexture mVideoView;

    private Button mMiniPlayBtn;

    private ImageView mFullBtn;

    private ImageView mLoadingBar;

    private Surface videoSurface;

    protected OrientationUtils mOrientationUtils; //旋转工具类

    private VideoPlayerListener listener;

    private FrameImageLoadListener mFrameLoadListener;

    private ScreenEventReceiver mScreenReceiver;

    private boolean mTouchingProgressBar = false;

    private float mDownX;//触摸的X

    private float mDownY; //触摸的Y

    private float mMoveY;

    private boolean mChangeVolume = false;//是否改变音量

    private boolean mChangePosition = false;//是否改变播放进度

    private boolean mShowVKey = true; //触摸显示虚拟按键

    private boolean mBrightness = false;//是否改变亮度

    private boolean mFirstTouch = true;//是否首次触摸

    private boolean mIsTouchWiget = mIfCurrentIsFullscreen;//是否可以滑动界面改变进度，声音等

    private int mThreshold = 50; //手势偏差值

    private int mSeekEndOffset; //手动滑动的起始偏移位置

    private int mDownPosition; //手指放下的位置

    private int mGestureDownVolume; //手势调节音量的大小

    private int mSeekTimePosition; //手动改变滑动的位置

    private int mScreenHeight; //屏幕高度

    private float mBrightnessData = -1; //亮度

    private RelativeLayout mTextureViewGroup;

    private IjkMediaPlayer mMediaPlayer;

    private VideoPlayerManager mPlayerManager;

    private SeekBar mProgressBar;

    private TextView mTotalTimeTextView;

    private TextView mCurrentTimeTextView;

    protected int mBuffterPoint;//缓存进度

    private ProgressBar mDialogVolumeProgressBar;

    private ImageView mBackButton;

    private View dummyRl;

    private boolean isShowDummyView = true;

    private Dialog mVolumeDialog;

    protected Dialog mProgressDialog;

    protected ProgressBar mDialogProgressBar;

    protected TextView mDialogSeekTime;

    protected TextView mDialogTotalTime;

    protected ImageView mDialogIcon;

    protected boolean mActionBar = true;//是否需要在利用window实现全屏幕的时候隐藏actionbar

    protected boolean mStatusBar = true;//是否需要在利用window实现全屏幕的时候隐藏statusbar

    protected int[] mListItemRect;//当前item框的屏幕位置

    protected int[] mListItemSize;//当前item的大小

    private static final int FULLSCREEN_ID = 0x0123;

    private static final int FULLSCREEN_ID2 = 0x01234;

    private int mSystemUiVisibility;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TIME_MSG:
                    if (mPlayerManager.isPlaying()) {
                        if (listener != null) {
                            listener.onBufferUpdate(mPlayerManager.getCurrentPosition());
                        }
                        sendEmptyMessageDelayed(TIME_MSG, TIME_INVAL);//
                    }
                    break;
                case PROGRESS_MSG:
                    if (mPlayerManager.isPlaying()) {
                        if (mMediaPlayer.getDuration() >= mMediaPlayer.getCurrentPosition()) {
                            if (playerState != STATE_IDLE) {
                                setTextAndProgress();
                                //循环清除进度
                                if (mBuffterPoint == 0 && mProgressBar.getProgress() >= (mProgressBar.getMax() - 1)) {
                                    loopSetProgressAndTime();
                                }
                                sendEmptyMessageDelayed(PROGRESS_MSG, TIME_INVAL);//

                            }
                        } else {
                            removeCallbacksAndMessages(PROGRESS_MSG);
                        }
                    }
                    break;
            }


        }
    };


    public VideoPlayerView(Context context) {
        super(context);
        initConfig();
        initView();
        registerBroadcastReceiver();
    }

    //设置父布局
    public void setViewGroup(ViewGroup viewGroup) {
        mViewGroup = viewGroup;
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
        mSeekEndOffset = VideoUtil.dip2px(getContext(), 50);
        mScreenHeight = displayMetrics.heightPixels;
    }

    private void initView() {
        view = LayoutInflater.from(getContext()).inflate(R.layout.xadsdk_video_player, this);
        mVideoView = new VideoTexture(getContext());
        mTextureViewGroup = (RelativeLayout) view.findViewById(R.id.texture_viewgroup);
        mTextureViewGroup.setOnTouchListener(this);
        mTextureViewGroup.setOnClickListener(this);
        mBottomProgressBar = (ProgressBar) findViewById(R.id.bottom_progressbar);
        mProgressBar = (SeekBar) findViewById(R.id.progress);
        mProgressBar.setOnTouchListener(this);
        mProgressBar.setOnSeekBarChangeListener(this);
        mTotalTimeTextView = (TextView) findViewById(R.id.total);
        mCurrentTimeTextView = (TextView) findViewById(R.id.current);
        mBackButton = (ImageView) findViewById(R.id.back);
        mBackButton.setOnClickListener(this);
        mTextureViewGroup.addView(mVideoView);
        mVideoView.setOnClickListener(this);
        mVideoView.setKeepScreenOn(true);
        mVideoView.setSurfaceTextureListener(this);
        initSmallLayoutMode();
        dummyRl = findViewById(R.id.dummy_rl);
        mVideoView.setOnClickListener(this);
    }

    // 小模式状态
    private void initSmallLayoutMode() {
        LayoutParams params = new LayoutParams(mScreenWidth, mDestationHeight);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        view.setLayoutParams(params);
        mMiniPlayBtn = (Button) view.findViewById(R.id.xadsdk_small_play_btn);
        mFullBtn = (ImageView) view.findViewById(R.id.xadsdk_to_full_view);
        mLoadingBar = (ImageView) view.findViewById(R.id.loading_bar);
        mMiniPlayBtn.setOnClickListener(this);
        mFullBtn.setOnClickListener(this);
        showPauseView(false);
    }

    /**
     * 设置播放连接
     */
    public void setDataUrl(String url) {
        this.mUrl = url;
        if (mIfCurrentIsFullscreen) {
            mBackButton.setVisibility(View.VISIBLE);
        } else {
            mBackButton.setVisibility(View.GONE);
        }
    }

    /**
     * 是否显示虚拟按键
     */
    private void showDummyView(boolean isShow) {
        if (playerState != STATE_PLAYING) {
            return;
        }
        dummyRl.setVisibility(isShow ? VISIBLE : INVISIBLE);
        isShowDummyView = isShow;
        if (isShow) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    showDummyView(false);
                    if (mIfCurrentIsFullscreen) {
                        hideNavKey(getContext());
                    }
                }
            }, 3000);
        }

    }


    public void isShowFullBtn(boolean isShow) {
//        mFullBtn.setImageResource(isShow ? R.drawable.xadsdk_ad_mini : R.drawable.xadsdk_ad_mini_null);
        mFullBtn.setVisibility(isShow ? View.VISIBLE : View.GONE);
        mBackButton.setVisibility(!isShow ? View.VISIBLE : View.GONE);
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
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
    }

    private void showStopView(boolean isShow) {
        mMiniPlayBtn.setVisibility(VISIBLE);
        mMiniPlayBtn.setBackground(isShow ? getResources().getDrawable(R.drawable.video_pause_normal) :
                getResources().getDrawable(R.drawable.video_play_normal));
    }

    private void showLoadingView() {
        mFullBtn.setVisibility(View.GONE);
        mLoadingBar.setVisibility(View.VISIBLE);
        AnimationDrawable anim = (AnimationDrawable) mLoadingBar.getBackground();
        anim.start();
        mMiniPlayBtn.setVisibility(View.GONE);
        loadFrameImage();
    }

    private void showPlayView() {
        setCurrentPlayState(STATE_PLAYING);
        mLoadingBar.clearAnimation();
        mLoadingBar.setVisibility(View.GONE);
    }

    //播放结束
    private void playerOver() {
        mBottomProgressBar.setProgress(100);
        mProgressBar.setProgress(100);
        mCurrentTimeTextView.setText(mTotalTimeTextView.getText());
    }


    @Override
    public boolean onInfo(int what, int extra) {
        return false;
    }

    @Override
    public void onBufferingUpdate(int percent) {
        if (percent != 0) {
            setSecondaryProgress(percent);
            mBuffterPoint = percent;
        }

    }

    protected void setTextAndProgress() {
        int position = getCurrentPositionWhenPlaying();
        int duration = getDuration();
        int progress = position * 100 / (duration == 0 ? 1 : duration);
        setProgressAndTime(progress, position, duration);
    }

    private void setSecondaryProgress(int secProgress) {
        if (secProgress > 94) {
            secProgress = 100;
        }
        if (secProgress != 0) {
            mProgressBar.setSecondaryProgress(secProgress);
            mBottomProgressBar.setSecondaryProgress(secProgress);
        }
    }

    protected void setProgressAndTime(int progress, int currentTime, int totalTime) {
        if (!mTouchingProgressBar) {
            if (progress != 0) {
                mProgressBar.setProgress(progress);
                mBottomProgressBar.setProgress(progress);
            }
        }
        mTotalTimeTextView.setText(VideoUtil.stringForTime(totalTime));
        if (currentTime > 0)
            mCurrentTimeTextView.setText(VideoUtil.stringForTime(currentTime));
    }

    public void setFrameURI(String url) {
        mFrameURI = url;
    }

    @Override
    public void onVideoSizeChanged() {
        int mVideoWidth = mPlayerManager.getCurrentVideoWidth();
        int mVideoHeight = mPlayerManager.getCurrentVideoHeight();
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            mVideoView.requestLayout();
        }
    }

    @Override
    public void onSeekComplete() {
        if (!isComplete() || !isRealPause()) {
            showPlayView();
        } else {
            loopSetProgressAndTime();
        }
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
            mPlayerManager.setMute(false);
            mMediaPlayer.setDataSource(this.mUrl);
            mMediaPlayer.prepareAsync(); //开始异步加载
        } catch (Exception e) {
            e.printStackTrace();
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
        mHandler.removeCallbacksAndMessages(TIME_MSG);
    }

    //恢复播放
    public void resume() {
        if (playerState != STATE_PAUSING) {
            return;
        }
        Log.e(TAG, "do resume:" + mMediaPlayer.isPlaying());
        mHandler.sendEmptyMessage(PROGRESS_MSG);
        if (!mMediaPlayer.isPlaying()) {
            entryResumeState();
//            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.start();
            mHandler.sendEmptyMessage(TIME_MSG);
            showPauseView(true);
        } else {
            entryResumeState();
            showPauseView(true);
            mHandler.sendEmptyMessage(TIME_MSG);
        }

    }

    //视频停止
    public void stop() {
        Log.e(TAG, " do stop");
        if (this.mMediaPlayer != null) {
            this.mMediaPlayer.reset();
            this.mMediaPlayer.setOnSeekCompleteListener(null);
            this.mMediaPlayer.stop();
//            this.mMediaPlayer.release();
//            this.mMediaPlayer = null;
        }
        mHandler.removeCallbacksAndMessages(TIME_MSG);
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
//        showPauseView(true);
        showDummyView(true);
        showStopView(false);
        setCurrentPlayState(STATE_PAUSING);
        mHandler.removeCallbacksAndMessages(TIME_MSG);
        if (mMediaPlayer != null) {
//            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.seekTo(0);
            mMediaPlayer.pause();
        }

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
        mHandler.removeCallbacksAndMessages(TIME_MSG); //release all message and runnable
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
        mPlayerManager.getMediaPlayer().seekTo(position);
        mPlayerManager.getMediaPlayer().setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
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
        if (mPlayerManager.isPlaying()) {
            mPlayerManager.getMediaPlayer().seekTo(position);
            mPlayerManager.getMediaPlayer().setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(IMediaPlayer iMediaPlayer) {
                    Log.e(TAG, "do seek and pause");
                    iMediaPlayer.pause();
                    mHandler.removeCallbacksAndMessages(TIME_MSG);
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
        Log.e(TAG, "onVisibilityChanged:" + visibility + "STATE_PAUSING:" + STATE_PAUSING);
        if (visibility == VISIBLE && playerState == STATE_PAUSING) {
            Log.e(TAG, "onVisibilityChanged" + visibility);
            if (isRealPause() || isComplete()) {
                pause();
            } else {
                if (mIfCurrentIsSmallscreen) {
                    setCurrentPlayState(STATE_PAUSING);
                    resume();
                    mIfCurrentIsSmallscreen = false;
                } else {
                    decideCanPlay();
                }
            }
        } else {
            Log.e(TAG, "onVisibilityChanged" + visibility);
            pause();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    public boolean isTouchWiget() {
        return mIsTouchWiget;
    }

    public void setIsTouchWiget(boolean isTouchWiget) {
        this.mIsTouchWiget = isTouchWiget;
    }

    @Override

    public boolean onTouch(View view, MotionEvent event) {
        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int id = view.getId();
        float x = event.getX();
        float y = event.getY();
        if (id == R.id.texture_viewgroup) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchingProgressBar = true;
                    mDownX = x;
                    mDownY = y;
                    mMoveY = 0;
                    mChangeVolume = false;
                    mChangePosition = false;
                    mShowVKey = false;
                    mBrightness = false;
                    mFirstTouch = true;

                    break;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);

                    if (mIfCurrentIsFullscreen || mIsTouchWiget) {

                        if (!mChangePosition && !mChangeVolume && !mBrightness) {
                            if (absDeltaX > mThreshold || absDeltaY > mThreshold) {
                                if (absDeltaX >= mThreshold) {
                                    //防止全屏虚拟按键
                                    int screenWidth = VideoUtil.getScreenWidth(getContext());
                                    if (Math.abs(screenWidth - mDownX) > mSeekEndOffset) {
                                        mChangePosition = true;
                                        mDownPosition = getCurrentPositionWhenPlaying();
                                    } else {
                                        mShowVKey = true;
                                    }
                                } else {
                                    int screenHeight = VideoUtil.getScreenHeight(getContext());
                                    boolean noEnd = Math.abs(screenHeight - mDownY) > mSeekEndOffset;
                                    if (mFirstTouch) {
                                        mBrightness = (mDownX < mScreenWidth * 0.5f) && noEnd;
                                        mFirstTouch = false;
                                    }
                                    if (!mBrightness) {
                                        mChangeVolume = noEnd;
                                        mGestureDownVolume = mPlayerManager.getAudioManager().getStreamVolume(AudioManager.STREAM_MUSIC);
                                    }
                                    mShowVKey = !noEnd;
                                }
                            }
                        }
                    }

                    if (mChangePosition) {
                        int totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + deltaX * totalTimeDuration / mScreenWidth);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = VideoUtil.stringForTime(mSeekTimePosition);
                        String totalTime = VideoUtil.stringForTime(totalTimeDuration);
                        showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
                    } else if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mPlayerManager.getAudioManager().getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mPlayerManager.getAudioManager().setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumeDialog(volumePercent);
                    } else if (!mChangePosition && mBrightness) {
                        if (Math.abs(deltaY) > mThreshold) {
                            float percent = (-deltaY / mScreenHeight);
                            onBrightnessSlide(percent);
                            mDownY = y;
                        }
                    }


                    break;
                case MotionEvent.ACTION_UP:
                    showDummyView(!isShowDummyView);
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumeDialog();
                    dismissBrightnessDialog();
                    if (mChangePosition) {
                        showLoadingView();
//                        seekAndResume(mSeekTimePosition);
                        mMediaPlayer.seekTo(mSeekTimePosition);
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        mProgressBar.setProgress(progress);
                        mBottomProgressBar.setProgress(progress);

                    }
//                    //不要和隐藏虚拟按键后，滑出虚拟按键冲突
//                    if (mHideKey && mShowVKey) {
//                        return true;
//                    }
                    break;
            }
        } else if (id == R.id.progress) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mHandler.removeCallbacksAndMessages(PROGRESS_MSG);
                    ViewParent vpdown = getParent();
                    while (vpdown != null) {
                        vpdown.requestDisallowInterceptTouchEvent(true);
                        vpdown = vpdown.getParent();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mHandler.sendEmptyMessage(PROGRESS_MSG);
                    ViewParent vpup = getParent();
                    while (vpup != null) {
                        vpup.requestDisallowInterceptTouchEvent(false);
                        vpup = vpup.getParent();
                    }
                    mBrightnessData = -1f;
                    break;
            }
        }
        return false;
    }


    protected void showVolumeDialog(int volumePercent) {
        if (mVolumeDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.video_volume_dialog, null);
            mDialogVolumeProgressBar = ((ProgressBar) localView.findViewById(R.id.volume_progressbar));
            mVolumeDialog = new Dialog(getContext(), R.style.video_style_dialog_progress);
            mVolumeDialog.setContentView(localView);
            mVolumeDialog.getWindow().addFlags(8);
            mVolumeDialog.getWindow().addFlags(32);
            mVolumeDialog.getWindow().addFlags(16);
            mVolumeDialog.getWindow().setLayout(-2, -2);
            WindowManager.LayoutParams localLayoutParams = mVolumeDialog.getWindow().getAttributes();
            localLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            localLayoutParams.width = getWidth();
            localLayoutParams.height = getHeight();
            int location[] = new int[2];
            getLocationOnScreen(location);
            localLayoutParams.x = location[0];
            localLayoutParams.y = location[1];
            mVolumeDialog.getWindow().setAttributes(localLayoutParams);
        }
        if (!mVolumeDialog.isShowing()) {
            mVolumeDialog.show();
        }

        mDialogVolumeProgressBar.setProgress(volumePercent);
    }


    protected void showProgressDialog(float deltaX, String seekTime, int seekTimePosition, String totalTime, int totalTimeDuration) {
        if (mProgressDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.video_progress_dialog, null);
            mDialogProgressBar = ((ProgressBar) localView.findViewById(R.id.duration_progressbar));
            mDialogSeekTime = ((TextView) localView.findViewById(R.id.tv_current));
            mDialogTotalTime = ((TextView) localView.findViewById(R.id.tv_duration));
            mDialogIcon = ((ImageView) localView.findViewById(R.id.duration_image_tip));
            mProgressDialog = new Dialog(getContext(), R.style.video_style_dialog_progress);
            mProgressDialog.setContentView(localView);
            mProgressDialog.getWindow().addFlags(Window.FEATURE_ACTION_BAR);
            mProgressDialog.getWindow().addFlags(32);
            mProgressDialog.getWindow().addFlags(16);
            mProgressDialog.getWindow().setLayout(getWidth(), getHeight());
            WindowManager.LayoutParams localLayoutParams = mProgressDialog.getWindow().getAttributes();
            localLayoutParams.gravity = Gravity.TOP;
            localLayoutParams.width = getWidth();
            localLayoutParams.height = getHeight();
            int location[] = new int[2];
            getLocationOnScreen(location);
            localLayoutParams.x = location[0];
            localLayoutParams.y = location[1];
            mProgressDialog.getWindow().setAttributes(localLayoutParams);
        }
        if (!mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }

        mDialogSeekTime.setText(seekTime);
        mDialogTotalTime.setText(" / " + totalTime);
        if (totalTimeDuration > 0)
            mDialogProgressBar.setProgress(seekTimePosition * 100 / totalTimeDuration);
        if (deltaX > 0) {
            mDialogIcon.setBackgroundResource(R.drawable.video_forward_icon);
        } else {
            mDialogIcon.setBackgroundResource(R.drawable.video_backward_icon);
        }

    }

    protected void dismissProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }


    private void dismissVolumeDialog() {
        if (mVolumeDialog != null) {
            mVolumeDialog.dismiss();
            mVolumeDialog = null;
        }
    }

    protected Dialog mBrightnessDialog;

    protected TextView mBrightnessDialogTv;

    protected void showBrightnessDialog(float percent) {
        if (mBrightnessDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.video_brightness, null);
            mBrightnessDialogTv = (TextView) localView.findViewById(R.id.app_video_brightness);
            mBrightnessDialog = new Dialog(getContext(), R.style.video_style_dialog_progress);
            mBrightnessDialog.setContentView(localView);
            mBrightnessDialog.getWindow().addFlags(8);
            mBrightnessDialog.getWindow().addFlags(32);
            mBrightnessDialog.getWindow().addFlags(16);
            mBrightnessDialog.getWindow().setLayout(-2, -2);
            WindowManager.LayoutParams localLayoutParams = mBrightnessDialog.getWindow().getAttributes();
            localLayoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
            localLayoutParams.width = getWidth();
            localLayoutParams.height = getHeight();
            int location[] = new int[2];
            getLocationOnScreen(location);
            localLayoutParams.x = location[0];
            localLayoutParams.y = location[1];
            mBrightnessDialog.getWindow().setAttributes(localLayoutParams);
        }
        if (!mBrightnessDialog.isShowing()) {
            mBrightnessDialog.show();
        }
        if (mBrightnessDialogTv != null)
            mBrightnessDialogTv.setText((int) (percent * 100) + "%");
    }

    protected void dismissBrightnessDialog() {
        if (mBrightnessDialog != null) {
            mBrightnessDialog.dismiss();
            mBrightnessDialog = null;
        }
    }

    private void loopSetProgressAndTime() {
        mProgressBar.setProgress(0);
        mProgressBar.setSecondaryProgress(0);
        mBottomProgressBar.setProgress(0);
        mBottomProgressBar.setSecondaryProgress(0);
        mCurrentTimeTextView.setText(VideoUtil.stringForTime(0));
    }

    @Override
    public void onClick(View v) {
        if (mIfCurrentIsFullscreen) {
            hideNavKey(getContext());
        }
        if (v == mMiniPlayBtn) {
            Log.e(TAG, "mMiniPlayBtn:" + playerState);
            if (playerState == STATE_PAUSING) {
                showStopView(true);
                if (mIfCurrentIsFullscreen) {
                    loopSetProgressAndTime();
                    resume();
                } else {
                    if (VideoUtil.getVisiblePercent(mViewGroup)
                            > VIDEO_SCREEN_PERCENT) {
                        resume();
                    }
                }
            } else if (playerState == STATE_PLAYING) {
                showStopView(false);
                pause();
            } else {
                showStopView(false);
                if (mIfCurrentIsFullscreen) {
                    mMediaPlayer.reset();
                    load();
                } else {
                    load();
                }
            }
        } else if (v == mVideoView) {
            showDummyView(!isShowDummyView);
            if (listener != null) {
                listener.onClickPlay();
            }
        } else if (v == mFullBtn) {
//            mSeekOnStart = mPlayerManager.getCurrentPosition();
            startWindowFullscreen(getContext(), true, true);
            if (listener != null) {
                listener.onClickFullScreenBtn();
            }
        } else if (v == mBackButton) {
            clearFullscreenLayout();
        }


    }

    /**
     * 退出window层播放全屏效果
     */
    public void clearFullscreenLayout() {
        mIfCurrentIsFullscreen = false;
        mIsTouchWiget = false;
        int delay = 0;
        if (mOrientationUtils != null) {
            delay = mOrientationUtils.backToProtVideo();
            mOrientationUtils.setEnable(false);
            if (mOrientationUtils != null) {
                mOrientationUtils.releaseListener();
                mOrientationUtils = null;
            }
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                backToNormal();
            }
        }, delay);

    }


    /**
     * 回到正常效果
     */
    private void backToNormal() {
        ViewGroup vp = getViewGroup();
        View oldF = vp.findViewById(FULLSCREEN_ID);
        ViewGroup viewGroup = (ViewGroup) vp.findViewById(FULLSCREEN_ID2);
        viewGroup.removeAllViews();
        VideoPlayerView videoPlayerView;
        if (oldF != null) {
            videoPlayerView = (VideoPlayerView) oldF;
            resolveNormalVideoShow(oldF, vp, videoPlayerView);
        } else {
            resolveNormalVideoShow(null, vp, null);
        }
    }

    /**
     * 恢复
     */
    protected void resolveNormalVideoShow(View oldF, ViewGroup vp, VideoPlayerView videoPlayerView) {
        mIfCurrentIsSmallscreen = true;
        mViewGroup.removeAllViews();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, mDestationHeight);
        if (oldF != null && oldF.getParent() != null) {
            ViewGroup viewGroup = (ViewGroup) oldF.getParent();
            vp.removeView(viewGroup);
        }
        if (mMediaPlayer.isPlaying() && playerState == STATE_PLAYING) {
            showPauseView(false);
        } else {
            showPauseView(true);
        }
        setCurrentPlayState(STATE_PAUSING);
        mViewGroup.setLayoutParams(lp);
        addTextureView();
        videoPlayerView.setViewGroup(mViewGroup);
        mViewGroup.addView(videoPlayerView, lp);
        mIfCurrentIsFullscreen = false;
        mIsTouchWiget = false;
        showNavKey(getContext(), mSystemUiVisibility);
        VideoUtil.showSupportActionBar(getContext(), mActionBar, mStatusBar);
        //TODO 显示全屏按键
        isShowFullBtn(true);
    }

    public static void showNavKey(Context context, int systemUiVisibility) {
        ((Activity) context).getWindow().getDecorView().setSystemUiVisibility(systemUiVisibility);
    }


    /**
     * 获取当前播放进度
     */
    public int getCurrentPositionWhenPlaying() {
        int position = 0;
        if (playerState == STATE_PLAYING || playerState == STATE_PAUSING) {
            try {
                position = mPlayerManager.getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    /**
     * 获取当前总时长
     */
    public int getDuration() {
        int duration = 0;
        try {
            duration = mPlayerManager.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    /**
     * 滑动改变亮度
     */
    private void onBrightnessSlide(float percent) {
        mBrightnessData = ((Activity) (getContext())).getWindow().getAttributes().screenBrightness;
        if (mBrightnessData <= 0.00f) {
            mBrightnessData = 0.50f;
        } else if (mBrightnessData < 0.01f) {
            mBrightnessData = 0.01f;
        }
        WindowManager.LayoutParams lpa = ((Activity) (getContext())).getWindow().getAttributes();
        lpa.screenBrightness = mBrightnessData + percent;
        if (lpa.screenBrightness > 1.0f) {
            lpa.screenBrightness = 1.0f;
        } else if (lpa.screenBrightness < 0.01f) {
            lpa.screenBrightness = 0.01f;
        }
        showBrightnessDialog(lpa.screenBrightness);
        ((Activity) (getContext())).getWindow().setAttributes(lpa);
    }

    /**
     * TextureView就绪
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (mPlayerManager.isPlaying()) {
            showPlayView();
            showDummyView(false);
            showStopView(true);
            mHandler.sendEmptyMessage(PROGRESS_MSG);
        }
        Log.e(TAG, "onSurfaceTextureAvailable");
        videoSurface = new Surface(surface);
        mPlayerManager.showDisplay(videoSurface);
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        mPlayerManager.showDisplay(null);
        surface.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//        Log.e(TAG, "onSurfaceTextureUpdated");
    }

    @Override
    public void onCompletion() {
        Log.e(TAG, "onCompletion");
        if (listener != null) {
            listener.onAdVideoLoadComplete();
        }
        playerOver();
        setIsComplete(true);
        setIsRealPause(true);
        mHandler.removeCallbacksAndMessages(PROGRESS_MSG);
        playBack();
    }

    /**
     * 播放器播放异常
     */
    @Override
    public void onError(int i, int i1) {
        Log.e(TAG, "onError");
        setCurrentPlayState(STATE_ERROR);
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
        }
        if (mCurrentCount >= LOAD_TOTAL_COUNT) {
            if (listener != null) {
                listener.onAdVideoLoadFailed();
            }
            showPauseView(false);
        }
        stop();
    }

    /**
     * 播放器就绪状态
     */
    @Override
    public void onPrepared() {
        showPauseView(true);
//        entryResumeState();
        showStopView(true);
        mCurrentCount = 0;
        if (listener != null) {
            listener.onAdVideoLoadSuccess();
        }
        //满足自动播放条件，则直接播放
        if (VideoUtil.canAutoPlay(getContext(),
                VideoUtil.getCurrentSetting()) &&
                VideoUtil.getVisiblePercent(mViewGroup) > VIDEO_SCREEN_PERCENT) {
            setCurrentPlayState(STATE_PAUSING);
            resume();
        } else {
            setCurrentPlayState(STATE_PLAYING);
            pause();
        }
        showDummyView(false);
    }

    public void setId() {
        setId(FULLSCREEN_ID);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
//        if (GSYVideoManager.instance().getMediaPlayer() != null && mHadPlay) {
        int time = seekBar.getProgress() * getDuration() / 100;
        mMediaPlayer.seekTo(time);
//        }
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
                    Log.e(TAG, "isRealPause():" + isComplete() + "isRealPause():" + isComplete() + "isIfCurrentIsFullscreen():" + isIfCurrentIsFullscreen());
                    if (playerState == STATE_PAUSING) {
                        if (isIfCurrentIsFullscreen()) {
                            hideNavKey(context);
                            if (isRealPause() || isComplete()) {
                                pause();
                            } else {
                                setCurrentPlayState(STATE_PAUSING);
                                resume();
                            }
                        } else {
                            showNavKey(getContext(), mSystemUiVisibility);
                            if (isRealPause() || isComplete()) {
                                //手动点的暂停，回来后还暂停
                                pause();
                            } else {
                                decideCanPlay();
                            }
                        }
                    }

                    break;
                case Intent.ACTION_SCREEN_OFF:
                    if (playerState == STATE_PLAYING) {
                        pause();
                    }
                    break;
            }
        }

    }

    //注册锁屏监听
    private void registerBroadcastReceiver() {
        if (mScreenReceiver == null) {
            mScreenReceiver = new ScreenEventReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            getContext().registerReceiver(mScreenReceiver, filter);
        }
    }

    //注销锁屏监听
    private void unRegisterBroadcastReceiver() {
        if (mScreenReceiver != null) {
            getContext().unregisterReceiver(mScreenReceiver);
        }
    }

    //判断是否在屏幕展示的范围
    private void decideCanPlay() {
        if (VideoUtil.getVisiblePercent(mViewGroup) > VIDEO_SCREEN_PERCENT) {
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
                    } else {
                    }
                }
            });
        }
    }

    //全屏播放
    private void resolveFullVideoShow(Context context, final VideoPlayerView videoPlayer, final RelativeLayout frameLayout) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) videoPlayer.getLayoutParams();
        lp.setMargins(0, 0, 0, 0);
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        videoPlayer.setLayoutParams(lp);
        videoPlayer.setIfCurrentIsFullscreen(true);
        mOrientationUtils = new OrientationUtils((Activity) context, videoPlayer);
        mOrientationUtils.setEnable(false);//不可自动旋转
        videoPlayer.mOrientationUtils = mOrientationUtils;
        mOrientationUtils.resolveByClick();//直接横屏
        Log.e(TAG, "showFull");
        videoPlayer.setVisibility(VISIBLE);
        frameLayout.setVisibility(VISIBLE);
        mIfCurrentIsFullscreen = true;
        mIsTouchWiget = true;
    }


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
                viewGroup.removeView(old);
            }
        }
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

        hideNavKey(context);//隐藏虚拟按键

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
            relativeLayout.setId(FULLSCREEN_ID2);
            relativeLayout.setBackgroundColor(Color.BLACK);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(getWidth(), getHeight());
            videoPlayerView.setViewGroup(relativeLayout);
            relativeLayout.addView(videoPlayerView, lp);
            vp.addView(relativeLayout, lpParent);
            videoPlayerView.setVisibility(INVISIBLE);
            relativeLayout.setVisibility(INVISIBLE);
            resolveFullVideoShow(context, videoPlayerView, relativeLayout);
            videoPlayerView.setDataUrl(mUrl);
            videoPlayerView.addTextureView();
            setCurrentPlayState(STATE_PLAYING);
            mPlayerManager.setManagerListener(videoPlayerView);
            return videoPlayerView;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 添加播放的view
     */
    protected void addTextureView() {
        if (mTextureViewGroup.getChildCount() > 0) {
            mTextureViewGroup.removeAllViews();
        }
        mVideoView = null;
        mVideoView = new VideoTexture(getContext());
        mVideoView.setSurfaceTextureListener(this);
        mVideoView.setRotation(mPlayerManager.getRotate());

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mTextureViewGroup.addView(mVideoView, layoutParams);
    }


    public static void hideSupportActionBar(Context context, boolean actionBar, boolean statusBar) {
        if (actionBar) {
            AppCompatActivity appCompatActivity = VideoUtil.getAppCompActivity(context);
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
                VideoUtil.getAppCompActivity(context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
    }

    public boolean isIfCurrentIsFullscreen() {
        return mIfCurrentIsFullscreen;
    }

    public void setIfCurrentIsFullscreen(boolean ifCurrentIsFullscreen) {
        this.mIfCurrentIsFullscreen = ifCurrentIsFullscreen;
    }
}

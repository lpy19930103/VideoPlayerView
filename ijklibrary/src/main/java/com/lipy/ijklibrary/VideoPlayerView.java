package com.lipy.ijklibrary;


import com.lipy.ijklibrary.listener.FrameImageLoadListener;
import com.lipy.ijklibrary.listener.ImageLoaderListener;
import com.lipy.ijklibrary.listener.VideoPlayerListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import tv.danmaku.ijk.media.exo.IjkExoMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * 视频播放view
 * Created by lipy on 2017/3/12.
 */

public class VideoPlayerView extends RelativeLayout implements IjkExoMediaPlayer.OnPreparedListener,
        IjkExoMediaPlayer.OnBufferingUpdateListener, IjkExoMediaPlayer.OnCompletionListener, IjkExoMediaPlayer.OnErrorListener,
        View.OnClickListener, TextureView.SurfaceTextureListener, IjkExoMediaPlayer.OnInfoListener, IjkExoMediaPlayer.OnSeekCompleteListener {


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


    private boolean canPlay = true;//是否
    private boolean mIsRealPause;//是否真正暂停
    private boolean mIsComplete;//是否播放完成
    private int mCurrentCount;//当前重试次数
    private int playerState = STATE_IDLE;//播放状态，默认闲置


    private View view;
    private ViewGroup mViewGroup;
    private AudioManager mAudioManager;
    private TextureView mVideoView;
    private Button mMiniPlayBtn;
    private ImageView mFullBtn;
    private ImageView mLoadingBar;
    private ImageView mFrameView;
    private Surface videoSurface;

    private IjkExoMediaPlayer mediaPlayer;
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
        mVideoView = (TextureView) view.findViewById(R.id.xadsdk_player_video_textureView);
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

    private IjkExoMediaPlayer createMediaPlayer() {
        mediaPlayer = new IjkExoMediaPlayer(getContext());
        mediaPlayer.reset();
        mediaPlayer.setOnCompletionListener(this);
//        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setScreenOnWhilePlaying(true);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
//        mediaPlayer.setOnVideoSizeChangedListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
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
//        load();
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
        Log.e(TAG, "onSurfaceTextureUpdated");
    }

    /**
     * 播放器缓冲更新
     */
    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {
        Log.e(TAG, "onBufferingUpdate");
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
        showPlayView();
        mediaPlayer = (IjkExoMediaPlayer) iMediaPlayer;
        if (mediaPlayer != null) {
            mediaPlayer.setOnBufferingUpdateListener(this);
            mCurrentCount = 0;
            if (listener != null) {
                listener.onAdVideoLoadSuccess();
            }
            decideCanPlay();
        }
    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onSeekComplete(IMediaPlayer iMediaPlayer) {
        Log.e(TAG, "onSeekComplete");
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

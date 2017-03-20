package com.lipy.ijklibrary;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.view.Surface;

import tv.danmaku.ijk.media.exo.IjkExoMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;

import static android.content.ContentValues.TAG;

/**
 * Created by Administrator on 2017/3/20.
 */

public class VideoPlayerManager implements IMediaPlayer.OnPreparedListener, IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnVideoSizeChangedListener, IMediaPlayer.OnInfoListener {

    private static VideoPlayerManager videoManager;
    private static IjkExoMediaPlayer mediaPlayer;
    private VideoPlayerListener_ mListener;
    private static int mCurrentVideoWidth = 0;
    private static int mCurrentVideoHeight = 0;
    private int mRotate = 0; //针对某些视频的旋转信息做了旋转处理
    //    private Context mContext;
    private long mSeekOnStart = -1; //从哪个开始播放

    public static synchronized VideoPlayerManager getInstance(Context context) {
        if (videoManager == null) {
            videoManager = new VideoPlayerManager(context);
        }
        return videoManager;
    }

    private VideoPlayerManager(Context context) {
//        mContext = context;
        mediaPlayer = new IjkExoMediaPlayer(context);
        prepare();
    }

    public void setManagerListener(VideoPlayerListener_ videoPlayerListener) {
        mListener = videoPlayerListener;
    }

    /**
     * 播放器缓冲更新
     */

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {
        if (mListener != null) {
            mListener.onBufferingUpdate(i);
        }
    }

    /**
     * 播放器播放完成
     */
    @Override
    public void onCompletion(IMediaPlayer iMediaPlayer) {
        if (mListener != null) {
            mListener.onCompletion();
        }
    }

    @Override
    public boolean onError(IMediaPlayer iMediaPlayer, int i, int i1) {
        mediaPlayer.reset();
        if (mListener != null) {
            mListener.onError(i, i1);
        }
        return true;//返回true 自己处理异常
    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, int what, int extra) {
        if (extra == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            mRotate = extra;
        }
        if (mListener != null) {
            mListener.onInfo(what, extra);
        }
        return false;
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        Log.e(TAG, "onPrepared");

        if (isPlaying()) {
            return;
        }
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (mediaPlayer != null && mSeekOnStart > 0) {
            mediaPlayer.seekTo(mSeekOnStart);
            mSeekOnStart = 0;
        }
        if (mListener != null) {
            mListener.onPrepared();
        }
    }

    @Override
    public void onSeekComplete(IMediaPlayer iMediaPlayer) {
        Log.e(TAG, "onSeekComplete");
        if (mListener != null) {
            mListener.onSeekComplete();
        }
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int i, int i1, int i2, int i3) {
        mCurrentVideoWidth = iMediaPlayer.getVideoWidth();
        mCurrentVideoHeight = iMediaPlayer.getVideoHeight();
        if (mListener != null) {
            mListener.onVideoSizeChanged();
        }
    }

    public void prepare() {

        createMediaPlayer();
    }

    private IjkExoMediaPlayer createMediaPlayer() {
        mediaPlayer.reset();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setScreenOnWhilePlaying(true);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnVideoSizeChangedListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        return mediaPlayer;
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public long getmSeekOnStart() {
        return mSeekOnStart;
    }

    public void setmSeekOnStart(long mSeekOnStart) {
        this.mSeekOnStart = mSeekOnStart;
    }

    public static int getmCurrentVideoHeight() {
        return mCurrentVideoHeight;
    }

    public static int getmCurrentVideoWidth() {
        return mCurrentVideoWidth;
    }

    public int getmRotate() {
        return mRotate;
    }

    public void stop() {

        Log.e(TAG, "stop");
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.setOnSeekCompleteListener(null);
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mListener != null) {
            mListener.stop();
        }
    }

    //恢复播放
    public void resume() {

        if (mListener != null) {
            mListener.resume();
        }
    }

    public void load(String url) {
        Log.e(TAG, "load");
        if (mListener != null) {
            mListener.load();
        }
        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();//异步加载视频资源
        } catch (Exception e) {
            stop();
            e.printStackTrace();
        }
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

    public void pause() {
        Log.e(TAG, "pause STATE_PLAYING");
        if (mListener != null) {
            mListener.pause();
        }
        if (isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void start() {
        mediaPlayer.start();
    }

    public void playBack() {
        Log.e(TAG, "playBack");
        if (mListener != null) {
            mListener.playBack();
        }
        if (mediaPlayer != null) {
            mediaPlayer.setOnSeekCompleteListener(null);
            mediaPlayer.seekTo(0);
            mediaPlayer.pause();
        }
    }

    public void showDisplay(Surface surface) {
        if (surface == null && mediaPlayer != null) {
            mediaPlayer.setSurface(null);
        } else {
            Surface holder = surface;
            if (mediaPlayer != null && holder.isValid()) {
                mediaPlayer.setSurface(holder);
            }
            Log.e(TAG, "showDisplay: mediaPlayer+" + mediaPlayer);
            if (mediaPlayer instanceof IjkExoMediaPlayer) {
                Log.e(TAG, "showDisplay:" + mediaPlayer.getDuration() + "---" + mediaPlayer + "mediaPlayer.getCurrentPosition()");
//                showPlayView();
                if (mediaPlayer != null && mediaPlayer.getDuration() > 30
                        && mediaPlayer.getCurrentPosition() < mediaPlayer.getDuration()) {
                    mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 20);
                }
            }
        }
    }

    //是否静音
    public void setNeedMute(boolean needMute) {
        if (mediaPlayer != null) {
            if (needMute) {
                mediaPlayer.setVolume(0, 0);
            } else {
                mediaPlayer.setVolume(1, 1);
            }
        }
    }

    //销毁播放器
    public void destory() {
        Log.e(TAG, "destory");
        mediaPlayer.release();
        mediaPlayer = null;
        if (mListener != null) {
            mListener.destory();
        }

    }


}

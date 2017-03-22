package com.lipy.ijklibrary;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.view.Surface;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static android.content.ContentValues.TAG;

/**
 * IjkPlayer封装类
 * Created by lipy on 2017/3/20.
 */

public class VideoPlayerManager implements IMediaPlayer.OnPreparedListener, IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnVideoSizeChangedListener, IMediaPlayer.OnInfoListener {


    private AudioManager mAudioManager;
    private static VideoPlayerManager videoManager;
    private static IjkMediaPlayer mediaPlayer;
    private VideoPlayerManagerListener mListener;
    private static int mCurrentVideoWidth = 0;
    private static int mCurrentVideoHeight = 0;
    private int mRotate = 0; //针对某些视频的旋转信息做了旋转处理
    private long mSeekOnStart = -1; //从哪个开始播放

    public static synchronized VideoPlayerManager getInstance(Context context) {
        if (videoManager == null) {
            videoManager = new VideoPlayerManager(context);
        }
        return videoManager;
    }

    private VideoPlayerManager(Context context) {
        mediaPlayer = new IjkMediaPlayer();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        prepare();
    }

    public AudioManager getAudioManager() {
        return mAudioManager;
    }

    public IjkMediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void setManagerListener(VideoPlayerManagerListener videoPlayerListener) {
        mListener = videoPlayerListener;
    }

    /**
     * 播放器缓冲更新
     */


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
//        if (mediaPlayer != null) {
//            mediaPlayer.start();
//        }
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

    private IjkMediaPlayer createMediaPlayer() {
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

    public int getCurrentVideoHeight() {
        return mCurrentVideoHeight;
    }

    public int getCurrentVideoWidth() {
        return mCurrentVideoWidth;
    }

    public int getRotate() {
        return mRotate;
    }


    public int getCurrentPosition() {
        if (mediaPlayer != null) {
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

    public void showDisplay(Surface surface) {
        if (surface == null && mediaPlayer != null) {
            mediaPlayer.setSurface(null);
        } else {
            Surface holder = surface;
            if (mediaPlayer != null && holder.isValid()) {
                mediaPlayer.setSurface(holder);
            }
            Log.e(TAG, "showDisplay: mediaPlayer+" + mediaPlayer);
            if (mediaPlayer instanceof IjkMediaPlayer) {
                if (mediaPlayer != null && mediaPlayer.getDuration() > 30
                        && mediaPlayer.getCurrentPosition() < mediaPlayer.getDuration()) {
                    mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 20);
                }
            }
        }
    }

    //是否静音
//    public void setMute(boolean needMute) {
//        if (mediaPlayer != null) {
//            if (needMute) {
//                mediaPlayer.setVolume(0, 0);
//            } else {
//                mediaPlayer.setVolume(1, 1);
//            }
//        }
//    }

    public void setMute(boolean mute) {
        if (mediaPlayer != null && mAudioManager != null) {
            float volume = mute ? 0.0f : 1.0f;
            mediaPlayer.setVolume(volume, volume);
        }
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {
        if (mListener != null) {
            mListener.onBufferingUpdate(i);
        }
    }
}

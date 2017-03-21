package com.lipy.ijklibrary;

/**
 * IjkPlayer封装类回调接口
 * Created by lipy on 2017/3/20.
 */

interface VideoPlayerManagerListener {
    void onPrepared();

    boolean onInfo(int what, int extra);

    void onBufferingUpdate(int i);

    void onCompletion();

    void onError(int what, int extra);

    void onVideoSizeChanged();

    void onSeekComplete();

//    void load();
//
//    void pause();
//
//    void resume();
//
//    void stop();
//
//    void playBack();
//
//    void destory();
//
//    void seekAndResume(int time);
//
//    void seekAndPause(int time);
}

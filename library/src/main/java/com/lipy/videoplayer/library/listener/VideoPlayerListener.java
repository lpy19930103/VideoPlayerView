package com.lipy.videoplayer.library.listener;

/**
 * Created by lipy on 2017/3/12.
 */

public interface VideoPlayerListener {

    void onBufferUpdate(int time);

    void onClickFullScreenBtn();

    void onClickVideo();

    void onClickBackBtn();

    void onClickPlay();

    void onAdVideoLoadSuccess();

    void onAdVideoLoadFailed();

    void onAdVideoLoadComplete();

}

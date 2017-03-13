package com.lipy.videoplayer.library.listener;

/**
 * Created by lipy on 2017/3/12.
 */

public interface VideoPlayerListener {

    void onBufferUpdate(int time);//播放到第几秒

    void onClickFullScreenBtn();//跳转全屏

    void onClickVideo();//点击视频区域

    void onClickBackBtn();

    void onClickPlay();

    void onAdVideoLoadSuccess();

    void onAdVideoLoadFailed();

    void onAdVideoLoadComplete();

}

package com.lipy.videoplayerview;


import com.lipy.ijklibrary.VideoPlayerView;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity {
    private VideoPlayerView mVideoPlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("LIPY", "onCreate");
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.viewgroup);
        mVideoPlayerView = new VideoPlayerView(this);
        mVideoPlayerView.setId();
        mVideoPlayerView.setViewGroup(layout);
        mVideoPlayerView.setDataUrl("http://192.168.43.234:8080/miniapps/test.mp4");
//        mVideoPlayerView.setDataUrl("http://baobab.wdjcdn.com/14564977406580.mp4");
        layout.addView(mVideoPlayerView);
    }

    @Override
    protected void onDestroy() {
        mVideoPlayerView.destory();
        super.onDestroy();
    }
}

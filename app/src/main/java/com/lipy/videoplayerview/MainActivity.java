package com.lipy.videoplayerview;


import com.lipy.ijklibrary.VideoPlayerView;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity {
    protected static final int FULLSCREEN_ID = 85597;
    private VideoPlayerView mVideoPlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("LIPY", "onCreate");
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.viewgroup);
        mVideoPlayerView = new VideoPlayerView(this, true);
        mVideoPlayerView.setId(FULLSCREEN_ID);
        mVideoPlayerView.setViewGroup(layout);
        mVideoPlayerView.setDataUrl("http://baobab.wdjcdn.com/14564977406580.mp4");
        layout.addView(mVideoPlayerView);
    }

    @Override
    protected void onDestroy() {
        mVideoPlayerView.destoryView();
        super.onDestroy();
    }
}

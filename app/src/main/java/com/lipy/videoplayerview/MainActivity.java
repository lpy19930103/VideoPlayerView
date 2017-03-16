package com.lipy.videoplayerview;


import com.lipy.ijklibrary.VideoPlayerView;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity {

    private VideoPlayerView mVideoPlayerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.viewgroup);
        mVideoPlayerView = new VideoPlayerView(this, layout);
        mVideoPlayerView.setDataUrl("http://baobab.wdjcdn.com/14564977406580.mp4");
        layout.addView(mVideoPlayerView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mVideoPlayerView.destory();
    }
}

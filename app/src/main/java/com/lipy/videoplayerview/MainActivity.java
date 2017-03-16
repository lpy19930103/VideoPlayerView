package com.lipy.videoplayerview;

import com.lipy.videoplayer.library.VideoPlayerView;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.viewgroup);
        VideoPlayerView videoPlayerView = new VideoPlayerView(this, layout);
        videoPlayerView.setDataUrl("http://baobab.wdjcdn.com/14564977406580.mp4");
        layout.addView(videoPlayerView);
    }
}

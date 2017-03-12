package com.lipy.videoplayerview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RelativeLayout;

import com.lipy.videoplayer.library.VideoPlayerView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.viewgroup);
        VideoPlayerView videoPlayerView = new VideoPlayerView(this, layout);
        videoPlayerView.setDataUrl("http://192.168.1.125:8080/miniapps/test.mp4");
        layout.addView(videoPlayerView);
    }
}

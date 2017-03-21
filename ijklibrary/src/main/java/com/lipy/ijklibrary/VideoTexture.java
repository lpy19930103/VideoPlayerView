package com.lipy.ijklibrary;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * 横屏与竖屏的匹配
 * Created by lipy on 2017/3/20.
 */

public class VideoTexture extends TextureView {

    private int sizeW;

    private int sizeH;

    public VideoTexture(Context context) {
        super(context);
    }

    public VideoTexture(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int videoWidth = VideoPlayerManager.getInstance(getContext()).getCurrentVideoWidth();
        int videoHeight = VideoPlayerManager.getInstance(getContext()).getCurrentVideoHeight();

        int width = getDefaultSize(videoWidth, widthMeasureSpec);
        int height = getDefaultSize(videoHeight, heightMeasureSpec);

        int widthS = getDefaultSize(videoWidth, widthMeasureSpec);
        int heightS = getDefaultSize(videoHeight, heightMeasureSpec);


        if (videoWidth > 0 && videoHeight > 0) {

            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = heightSpecSize;

                if (videoWidth * height < width * videoHeight) {
                    width = height * videoWidth / videoHeight;
                } else if (videoWidth * height > width * videoHeight) {
                    height = width * videoHeight / videoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize;
                height = width * videoHeight / videoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                height = heightSpecSize;
                width = height * videoWidth / videoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    width = widthSpecSize;
                }
            } else {
                width = videoWidth;
                height = videoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    height = heightSpecSize;
                    width = height * videoWidth / videoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    width = widthSpecSize;
                    height = width * videoHeight / videoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }

        if (getRotation() != 0 && getRotation() % 90 == 0) {
            if (widthS < heightS) {
                if (width > height) {
                    width = (int) (width * (float) widthS / height);
                    height = widthS;
                } else {
                    height = (int) (height * (float) width / widthS);
                    width = widthS;
                }
            } else {
                if (width > height) {
                    height = (int) (height * (float) width / widthS);
                    width = widthS;
                } else {
                    width = (int) (width * (float) widthS / height);
                    height = widthS;
                }
            }
        }

        //如果设置了比例
//        if (GSYVideoType.getShowType() == GSYVideoType.SCREEN_TYPE_16_9) {
        if (height > width) {
            width = height * 9 / 16;
        } else {
            height = width * 9 / 16;
        }
//        } else if (GSYVideoType.getShowType() == GSYVideoType.SCREEN_TYPE_4_3) {
//            if (height > width) {
//                width = height * 3 / 4;
//            } else {
//                height = width * 3 / 4;
//            }
//        }

        sizeH = height;

        sizeW = width;

        setMeasuredDimension(width, height);
    }

    public int getSizeH() {
        return sizeH;
    }

    public int getSizeW() {
        return sizeW;
    }
}
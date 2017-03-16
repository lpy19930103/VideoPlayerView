package com.lipy.ijklibrary.listener;

import android.graphics.Bitmap;

/**
 * Created by lipy on 2017/3/12.
 */

public interface ImageLoaderListener {
    /**
     * 如果图片下载不成功，传null
     *
     * @param loadedImage
     */
    void onLoadingComplete(Bitmap loadedImage);
}

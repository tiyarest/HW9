package com.bytedance.ies.camerarecorddemoapp;

import android.text.TextUtils;
import android.util.Log;

public class FaceDetectHelper {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private static FaceDetectHelper mHelper = null;
    private OnFaceDetectedCallback mFaceDetectedCallback = null;

    private FaceDetectHelper() {
        nativeInit();
    }

    public static FaceDetectHelper getHelper() {
        synchronized (FaceDetectHelper.class) {
            if (mHelper == null) {
                synchronized (FaceDetectHelper.class) {
                    if (mHelper == null) {
                        mHelper = new FaceDetectHelper();
                    }
                }
            }
        }
        return mHelper;
    }

    public void destroy() {
        nativeUnInit();
        mHelper = null;
    }

    public void setFaceDetectModelPath(String modelPath) {
        if (!TextUtils.isEmpty(modelPath)) {
            nativeSetFaceDetectModelPath(modelPath);
        }
    }

    public void setLicense(String license) {
        nativeSetLicense(license);
    }

    public void setFaceDetectedCallback(OnFaceDetectedCallback callback) {
        mFaceDetectedCallback = callback;
    }

    public void writeBMP() {
        nativeTestWriteBmp();
    }

    public interface OnFaceDetectedCallback {
        void onFaceDetected(int ret,int left ,int right ,int bottom,int top);
    }

    public void detectFace(byte[] image, int pixelFormat, int width, int height, int stride) {
        nativeDetectFace(image, pixelFormat, width, height, stride);
    }

    public void onFaceDetectedCallback(int ret,int left ,int right ,int bottom,int top) {
        if (mFaceDetectedCallback != null) {
            mFaceDetectedCallback.onFaceDetected(ret,left,right,bottom,top);
        }
    }

    //TODO: add fact rect points through the params in the callback
    public static void nativeOnFaceDetectedCallback(int ret,int left ,int right ,int bottom,int top) {
        Log.d("FaceDetectHelper", "JAVA detectFaceCallbackMethod ret : " + ret);
        if (mHelper != null) {
            mHelper.onFaceDetectedCallback(ret,left,right,bottom,top);
        }
    }

    private native void nativeSetLicense(String license);

    private native void nativeSetFaceDetectModelPath(String effectModePath);

    private native void nativeInit();

    private native void nativeUnInit();

    private native void nativeDetectFace(byte[] image, int pixelFormat, int width, int height, int stride);

    private native void nativeTestWriteBmp();
}

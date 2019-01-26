# HW9
第九次作业
jnilbs里面放了两个封装好的库，用于实现人脸识别。人脸识别使用c++实现，也作为一个库来被java程序调用。  

CMakeLists.txt中我们对整个项目调用的库进行说明  

例如：我们从外部引入libeffect库，是一个动态库，在项目中的地址为${CMAKE_SOURCE_DIR}/libs/armeabi-v7a/libeffect.so。  

        add_library( libeffect
        SHARED
        IMPORTED )
        set_target_properties( # Specifies the target library.
        libeffect
        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION
        # Provides the path to the library you want to import.
        "${CMAKE_SOURCE_DIR}/libs/armeabi-v7a/libeffect.so")
        
我们自己写的c++库：  

        add_library( # Sets the name of the library.
        native-lib

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/cpp/native-lib.cpp
        src/main/cpp/TEUtils.cpp
        src/main/cpp/facedetect/FaceDetectHelper.cpp)

        include_directories(
        ${CMAKE_SOURCE_DIR}/src/main/cpp/include
        ${CMAKE_SOURCE_DIR}/src/main/cpp/include/libyuv
        ${CMAKE_SOURCE_DIR}/src/main/cpp/facedetect
        )  

我们自己写的native-lib会调用libyuv，libeffect
        target_link_libraries( # Specifies the target library.
         native-lib

        # Links the target library to the log library
        # included in the NDK.
        libyuv libeffect ${log-lib})

实现人脸识别具体的步骤：  

1.配置相机  

2. Camera拍摄预览中设置setPreviewCallback实现onPreviewFrame接口，实时截取每一帧视频流数据  
将每一帧的数据流交给CameraBufferManager  

        mPreviewCallback = new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] bytes, Camera camera) {
                if (mCamera == null) {
                    return;
                }
               
                CameraBufferManager.getCameraBufferManager().addCameraBuffer(bytes);
            }
        };
        
CameraBufferManager来讲每一帧的数据加入一个队列里面，开启一个新的线程来处理队列里面的buffer。新的线程开启检测。   

        private Thread mThread = new Thread() {
        public void run() {
            while (mStartThread) {
                if (mCameraBufferQueue == null) {
                    return;
                }
                try {
                    byte[] buffer = mCameraBufferQueue.remove();
                    if (buffer != null) {
                        FaceDetectHelper.getHelper().detectFace(buffer, 0, Config.VIDEO_HEIGHT, Config.VIDEO_WIDTH, Config.VIDEO_HEIGHT * 4);
                    }
                } catch (NoSuchElementException e) {
                    e.printStackTrace();
                }
            }
        }
    };

 在detectFace（）里面，调用c++的的检测方法。
 
        static {
        System.loadLibrary("native-lib");
    }
    
        public void detectFace(byte[] image, int pixelFormat, int width, int height, int stride) {
        nativeDetectFace(image, pixelFormat, width, height, stride);
    }
        
        private native void nativeDetectFace(byte[] image, int pixelFormat, int width, int height, int stride);        
        
期间我们需要jni来讲分析请求，将byte转换成c++可以识别的数据，调用c++方法  

        extern "C"
JNIEXPORT void JNICALL
Java_com_bytedance_ies_camerarecorddemoapp_FaceDetectHelper_nativeDetectFace(JNIEnv *env,
                                                                             jobject instance,
                                                                             jbyteArray imageByteArr,
                                                                             jint pixelFormat,
                                                                             jint width,
                                                                             jint height,
                                                                             jint stride) {
    jboolean copy = 1;
    unsigned char *data = (unsigned char *) env->GetByteArrayElements(imageByteArr, &copy);

    int length = width * height * 4;
    unsigned char *rgbBuf = (unsigned char *) malloc(length);

    libyuv::NV21ToARGB(data, width, data + width * height, width, rgbBuf, width * 4, width, height);

    if (mFaceDetectHelper != NULL) {
        mFaceDetectHelper->detectFace(rgbBuf, pixelFormat, width, height, stride);
    }
    free(rgbBuf);
    env->ReleaseByteArrayElements(imageByteArr, (jbyte *) data, 0);

}

在detectFace（）函数中，如果检测成功  

        if (result == BEF_RESULT_SUC) {
        if (mDetectFaceCallback != NULL) {
            LOGD("byted_effect_face_detect face count is %d", pFaceInfo.face_count);
            if (pFaceInfo.face_count > 0) {
                int len = sizeof(pFaceInfo.base_infos) / sizeof(pFaceInfo.base_infos[0]);
                LOGD("byted_effect_face_detect face info size : %d", len);
                for (int i = 0; i < len; ++i) {
                    bef_face_106 item = pFaceInfo.base_infos[i];
                    LOGD("byted_effect_face_detect face info action : %d - %d", i, item.action);
                    if (item.action > 0) {
                        //TODO: add face rect point
                        mDetectFaceCallback(item.action,item.rect.left,item.rect.right,item.rect.bottom,item.rect.top);
                    }
                }
            }
        }
        
通过mDetectFaceCallback回调给java，其中我们还需要jni进行一次转换  

        extern "C"
JNIEXPORT void JNICALL
Java_com_bytedance_ies_camerarecorddemoapp_FaceDetectHelper_nativeInit(JNIEnv *env,
                                                                       jobject instance) {
//    Android_JNI_GetEnv();

    faceDetectHelperClass = env->GetObjectClass(instance);

    if (faceDetectHelperClass != NULL) {
        detectFaceCallbackMethod = env->GetStaticMethodID(faceDetectHelperClass,
                                                          "nativeOnFaceDetectedCallback", "(IIIII)V");
        if (detectFaceCallbackMethod == NULL) {
            LOGE("detectFaceCallbackMethod NULL");
        } else {
            LOGE("detectFaceCallbackMethod success");
        }
    }

    mObj = env->NewGlobalRef(faceDetectHelperClass);

    if (mFaceDetectHelper == NULL) {
        mFaceDetectHelper = new FaceDetectHelper();
        mFaceDetectHelper->setDetectFaceCallback([](int ret,int l, int r, int t, int b) {
            JNIEnv *_env = Android_JNI_GetEnv();
            if (_env != NULL && detectFaceCallbackMethod && mObj != NULL) {
                LOGD("jni detectFaceCallbackMethod ret : %d,%d,%d,%d,%d", ret,l,r,l,t,b);
                _env->CallStaticVoidMethod((jclass) mObj, detectFaceCallbackMethod, ret,l,r,t,b);
            }
        });
    }

}
    

回调给jave：  

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
   
   对回调的数据进行处理，添加框框
        FaceDetectHelper.getHelper().setFaceDetectedCallback(new FaceDetectHelper.OnFaceDetectedCallback() {
            @Override
            public void onFaceDetected(final int ret,final int left ,final int right ,final int bottom,final int top) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //TODO:
                        // 1 将人脸表情等通过ICON展示在UI上面
                        // 2 增加人脸位置返回值之后，通过方框的图在UI上面现实人脸区域
//                        Canvas canvas = mSurfaceHolder.lockCanvas();
//                        paint = new Paint();
//                        paint.setColor(Color.BLUE);
//                        paint.setStrokeWidth(2);
//                        paint.setStyle(Paint.Style.STROKE);
//                        canvas.drawRect(new RectF(100, 100, 200, 200), paint)
                        ImageView imageView = findViewById(R.id.face_box);
                       // imageView.setHeight((int)((right-left)*1.5f));
                        //imageView.setWidth((int)((bottom-top)*1.5f));


                      //  LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams)imageView.getLayoutParams();

                        params.setMargins((int)(1080-bottom*1.5f),(int)(1920-right*1.5f),0,0);
                        params.height=(int)((right-left)*1.5f);
                        params.width=(int)((bottom-top)*1.5f);
                        imageView.setLayoutParams(params);



                       // tv.setText(left + "\n"+right+"\n"+top+"\n"+bottom);
                    }
                });













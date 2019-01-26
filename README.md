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
        
CameraBufferManager来讲每一帧的数据加入一个buffer队列里面，开启一个新的线程来处理队列里面的buffer。新的线程开启检测。   

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

        
        
        
        
        

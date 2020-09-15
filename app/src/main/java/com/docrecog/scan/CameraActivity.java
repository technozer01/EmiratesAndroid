/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.docrecog.scan;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.media.CameraProfile;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.accurascan.accuraemirates.sdk.FocusManager;
import com.accurascan.accuraemirates.sdk.FocusManager.Listener;
import com.accurascan.accuraemirates.sdk.R;
import com.accurascan.accuraemirates.sdk.OcrResultActivity;
import com.accurascan.accuraemirates.sdk.camera.CameraHolder;
import com.accurascan.accuraemirates.sdk.model.OcrData;
import com.accurascan.accuraemirates.sdk.motiondetection.SensorsActivity;
import com.google.android.gms.common.images.Size;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 *   This class is used to scan Emirates Id card
 *   And result will be display using OcrResultActivity.java class.
 *
 * */

public class CameraActivity extends SensorsActivity implements
        SurfaceHolder.Callback, Camera.PreviewCallback, Camera.ShutterCallback,
        Camera.PictureCallback, Listener, OnTouchListener, View.OnClickListener, OCRCallback {

    protected static final int IDLE = 0; // preview is active
    protected static final int SAVING_PICTURES = 5;
    private static final int PREVIEW_STOPPED = 0;
    // Focus is in progress. The exact focus state is in Focus.java.
    private static final int FOCUSING = 2;
    private static final int SNAPSHOT_IN_PROGRESS = 3;
    private static final int SELFTIMER_COUNTING = 4;
    private static final String TAG = "CameraActivity";
    private static final int FIRST_TIME_INIT = 0;
    private static final int CLEAR_SCREEN_DELAY = 1;
    private static final int SET_CAMERA_PARAMETERS_WHEN_IDLE = 4;
    // number clear
    private static final int TRIGER_RESTART_RECOG = 5;
    private static final int TRIGER_RESTART_RECOG_DELAY = 40; //30 ms
    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;
    private static final long VIBRATE_DURATION = 200L;
    private static boolean LOGV = false;
    private RecogEngine mCardScanner;
    private static int mRecCnt = 0; //counter for mrz detecting
    private Context mContext = null;
    private TextView mScanTitle = null;
    private TextView mScanMsg = null;
    private ImageView mFlipImage = null;
    private final Lock _mutex = new ReentrantLock(true);

    public OcrData ocrData;
    private RecogResult g_recogResult;
    // Save front and back scan document in cache directory
    private File fileCacheDirectory;

    /////////////////////
    //audio
    MediaPlayer mediaPlayer = null;
    AudioManager audioManager = null;

    final Handler mHandler = new MainHandler();
    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();
    private final AutoFocusCallback mAutoFocusCallback = new AutoFocusCallback();
    protected Camera mCameraDevice;
    // The first rear facing camera
    Parameters mParameters;
    SurfaceHolder mSurfaceHolder;
    // This handles everything about focus.
    FocusManager mFocusManager;
    int mPreviewWidth = 1280;
    int mPreviewHeight = 720;
    Size previewSize = null;
    // Use Surface view to display camera frame
    private SurfaceView preview;
    private boolean isPreviewSet = false; // one time preview set
    private byte[] cameraData;
    private Parameters mInitialParams;
    private int mCameraState = PREVIEW_STOPPED;
    private int mCameraId;
    private boolean mOpenCameraFail = false;
    private boolean mCameraDisabled = false, isTouchCalled;
    Thread mCameraOpenThread = new Thread(new Runnable() {
        public void run() {
            try {
                mCameraDevice = Util.openCamera(CameraActivity.this, mCameraId);
            } catch (Exception e) {
                mOpenCameraFail = true;
                mCameraDisabled = true;
            }
        }
    });
    private boolean mOnResumePending;
    private boolean mPausing;
    private boolean mFirstTimeInitialized;
    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;
    private View mPreviewFrame;
    RelativeLayout rel_main;// Preview frame area for SurfaceView.
    private boolean mbVibrate;
    private Dialog dialog;
    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mDisplayOrientation;
    private boolean mIsAutoFocusCallback = false;
    Thread mCameraPreviewThread = new Thread(new Runnable() {
        public void run() {
            initializeCapabilities();
            startPreview();
        }
    });

    private String cardType = "Emirates National ID";
    private String countryname = "United Arab Emirates";

    private OpenCvHelper openCvHelper;
    private boolean isbothavailable = true;
    private boolean isfront = true;
    private boolean isback = false;
    private String cardside = "Front";
    private int cardpos = 0;
    private int checkmrz = 0; //0 ideal 1 not require 2 require
    private int gotmrz = -1;
    private boolean isDone = false;
    private boolean isBackPressed = false;

    View rel_left, rel_right;
    int imgheight;
    private int rectW, rectH;
    DisplayMetrics dm;
    private int titleBarHeight = 0;
    private boolean doblurcheck = false;
    private boolean isBlurSet = false;

    private boolean isSupported(String value, List<String> supported) {
        return supported != null && supported.indexOf(value) >= 0;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (LOGV) Log.v(TAG, "onWindowFocusChanged.hasFocus=" + hasFocus
                + ".mOnResumePending=" + mOnResumePending);
        if (hasFocus && mOnResumePending) {
            doOnResume();
            mOnResumePending = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mbVibrate = true;
        if (LOGV) Log.v(TAG, "onResume. hasWindowFocus()=" + hasWindowFocus());
        if (mCameraDevice == null) {// && isKeyguardLocked()) {
            if (LOGV) Log.v(TAG, "onResume. mOnResumePending=true");
            if (hasWindowFocus()) {
                doOnResume();
            } else
                mOnResumePending = true;
        } else {
            if (LOGV) Log.v(TAG, "onResume. mOnResumePending=false");
            int currentSDKVersion = Build.VERSION.SDK_INT;

            doOnResume();


            mOnResumePending = false;
        }
    }

    protected void doOnResume() {
        if (mOpenCameraFail || mCameraDisabled)
            return;

        // if (mRecogService != null && mRecogService.isProcessing())
        // showProgress(null);
        mRecCnt = 0;
        mPausing = false;

        // Start the preview if it is not started.
        if (mCameraState == PREVIEW_STOPPED) {
            try {
                mCameraDevice = Util.openCamera(this, mCameraId);
                initializeCapabilities();
                startPreview();
            } catch (Exception e) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            }
        }

        if (mSurfaceHolder != null) {
            // If first time initialization is not finished, put it in the
            // message queue.
            if (!mFirstTimeInitialized) {
                mHandler.sendEmptyMessage(FIRST_TIME_INIT);
            } else {
                initializeSecondTime();
            }
        }

        keepScreenOnAwhile();
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.ivBack:
                onBackPressed();
                break;
        }
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized)
            return;

//		mOrientationListener = new MyOrientationEventListener(this);
//		mOrientationListener.enable();

        mCameraId = CameraHolder.instance().getBackCameraId();

        Util.initializeScreenBrightness(getWindow(), getContentResolver());
        mFirstTimeInitialized = true;
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        //mOrientationListener.enable();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        keepScreenOnAwhile();
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dm = getResources().getDisplayMetrics();
        mCameraId = CameraHolder.instance().getBackCameraId();
        //String str = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
        String[] defaultFocusModes = {"continuous-video", "auto", "continuous-picture"};
        mFocusManager = new FocusManager(defaultFocusModes);
        /*
         * To reduce startup time, we start the camera open and preview threads.
         * We make sure the preview is started at the end of onCreate.
         */
        mCameraOpenThread.start();

        // create and initialize the scan engine.
        if (mCardScanner == null) {
            mCardScanner = new RecogEngine();
            mCardScanner.initEngine(this, this);
            /**
             * Update filter value according to requirement
             * {@link RecogEngine#setFilter()}
             */
            mCardScanner.setFilter();
        }
        mContext = this;
        openCvHelper = new OpenCvHelper(this);
        ocrData = new OcrData();
        fileCacheDirectory = new File(getCacheDir(),"files");
        if (!fileCacheDirectory.exists()) {
            fileCacheDirectory.mkdir();
        }
        //initialize the result value
        //{{{
        mRecCnt = 0;
        g_recogResult = new RecogResult();
        g_recogResult.recType = RecType.INIT;
        g_recogResult.bRecDone = false;
        g_recogResult.bFaceReplaced = false;
        //}}}

        // Hide the window title.
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.camera_activity);

        try {
            Resources myResources = getResources();
            int idStatusBarHeight = myResources.getIdentifier("status_bar_height", "dimen", "android");
            if (idStatusBarHeight > 0) {
                titleBarHeight = getResources().getDimensionPixelSize(idStatusBarHeight);
            } else {
                titleBarHeight = 0;
            }
        } catch (Resources.NotFoundException e) {
            titleBarHeight = 0;
            e.printStackTrace();
        }
        if (LOGV) Log.i("a", "StatusBar Height= " + titleBarHeight);

        ocrData.setCardname(cardType);
        ocrData.setCountryname(countryname);

        rel_left = findViewById(R.id.rel_left);
        rel_right = findViewById(R.id.rel_right);
        rel_main = findViewById(R.id.rel_main);

        SetTempleteImage();
        findViewById(R.id.ivBack).setOnClickListener(this);

        mPreviewFrame = findViewById(R.id.camera_preview);
        mPreviewFrame.setOnTouchListener(this);
        isPreviewSet = false;
        preview = (SurfaceView) findViewById(R.id.camera_preview);

        SurfaceHolder holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mScanTitle = (TextView) findViewById(R.id.tv_side_msg);
        mScanTitle.setText("Scan Front Side of " + ocrData.getCardname());
        mScanMsg = (TextView) findViewById(R.id.tv_scan_msg);
        mFlipImage = (ImageView) findViewById(R.id.ivFlipImage);
        mFlipImage.setVisibility(View.INVISIBLE);

        ///audio init
        mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.beep);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Make sure camera device is opened.
        try {
            mCameraOpenThread.join();
            mCameraOpenThread = null;
            if (mOpenCameraFail) {
                Util.showErrorAndFinish(this, R.string.cannot_connect_camera);
                return;
            } else if (mCameraDisabled) {
                Util.showErrorAndFinish(this, R.string.camera_disabled);
                return;
            }
        } catch (InterruptedException ex) {
            // ignore
        }

        mCameraPreviewThread.start();

        // Make sure preview is started.
        try {
            mCameraPreviewThread.join();
        } catch (InterruptedException ex) {
            // ignore
        }
        mCameraPreviewThread = null;
        requestCameraPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK) {
            mRecCnt = 0;
            gotmrz = -1;
            g_recogResult = new RecogResult();
            ocrData = new OcrData();
            ocrData.setCardname(cardType);
            ocrData.setCountryname(countryname);
            mScanTitle.setText("Scan Front Side of " + ocrData.getCardname());
            SetFrontTemplete();
            isDone = false;
//            if (DEBUG) {
//                Toast.makeText(mContext, "Result ok", Toast.LENGTH_SHORT).show();
//            }
        }
    }

    @Override
    public void onDestroy() {

        // finalize the scan engine.
        if (mediaPlayer != null)
            mediaPlayer.release();
        Runtime.getRuntime().gc();
        mCardScanner.closeOCR(1);

        super.onDestroy();
        // unregister receiver.
    }

    @Override
    public void onBackPressed() {
        ocrData.setFrontData(null);
        ocrData.setBackData(null);
        if (isBackPressed) {
            super.onBackPressed();
        } else {
            Toast.makeText(CameraActivity.this, "Press again to exit app", Toast.LENGTH_SHORT).show();
            isBackPressed = true;
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                isBackPressed = false;
            }
        }, 2000);
    }


    /*When data scan successfully this method called and display result*/
    void showResultActivity() {
        Runtime.getRuntime().gc();
        if (!isDone) {
            isDone = true;
            Intent intent = new Intent();
            intent.setClass(this, OcrResultActivity.class);
            ocrData.setMrzData(g_recogResult);
            intent.putExtra("ocrData", ocrData);
            startActivityForResult(intent, 101);
            mCardScanner.closeOCR(0);
//            finishAffinity();

            playEffect();
        }
    }

    private void initializeCapabilities() {

        if (mCameraDevice != null)
            mInitialParams = mCameraDevice.getParameters();
//        mCameraDevice.autoFocus(new AutoFocusCallback());
        if (mInitialParams != null) {
            mInitialParams.getFocusMode();
            mFocusManager.initializeParameters(mInitialParams);
        }

        if (mCameraDevice != null) {
            mParameters = mCameraDevice.getParameters();
            GetCameraResolution();
        }
    }

    private void startPreview() {

        if (mCameraDevice != null) {
            if (mPausing || isFinishing())
                return;

            mCameraDevice.setErrorCallback(mErrorCallback);

            // If we're previewing already, stop the preview first (this will blank
            // the screen).
            if (mCameraState != PREVIEW_STOPPED)
                stopPreview();

            setPreviewDisplay(mSurfaceHolder);
            setDisplayOrientation();

            mCameraDevice.setOneShotPreviewCallback(CameraActivity.this);
            setCameraParameters(UPDATE_PARAM_ALL);

            // Inform the mainthread to go on the UI initialization.
            if (mCameraPreviewThread != null) {
                synchronized (mCameraPreviewThread) {
                    mCameraPreviewThread.notify();
                }
            }

            try {
                if (LOGV) Log.v(TAG, "startPreview");
                mCameraDevice.startPreview();
//                autoFocus();
            } catch (Throwable ex) {
                closeCamera();
//                throw new RuntimeException("startPreview failed", ex);
            }

            setCameraState(IDLE);

            // notify again to make sure main thread is wake-up.
            if (mCameraPreviewThread != null) {
                synchronized (mCameraPreviewThread) {
                    mCameraPreviewThread.notify();
                }
            }
        }
    }

    private void setPreviewDisplay(SurfaceHolder holder) {
        try {
            if (mCameraDevice != null) {
                mCameraDevice.setPreviewDisplay(holder);
            }
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = Util.getDisplayRotation(this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation,
                mCameraId);
        mCameraDevice.setDisplayOrientation(mDisplayOrientation);
    }

    private void stopPreview() {
        if (mCameraDevice == null)
            return;
        mCameraDevice.stopPreview();
        // mCameraDevice.setPreviewCallback(null);
        setCameraState(PREVIEW_STOPPED);
    }

    private void setCameraState(int state) {
        mCameraState = state;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            CameraHolder.instance().release();
            mCameraDevice.setErrorCallback(null);
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }

    @Override
    protected void onPause() {
        if (LOGV) Log.e(TAG, "onPause");

        mOnResumePending = false;
        mPausing = true;

        mIsAutoFocusCallback = false;

        stopPreview();

        // Close the camera now because other activities may need to use it.
        closeCamera();
        resetScreenOn();

        // Remove the messages in the event queue.
        mHandler.removeMessages(FIRST_TIME_INIT);
        mHandler.removeMessages(TRIGER_RESTART_RECOG);

//		if (mFirstTimeInitialized)
//			mOrientationListener.disable();

        super.onPause();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        // Make sure we have a surface in the holder before proceeding.
        if (holder.getSurface() == null) {
            if (LOGV) Log.d(TAG, "holder.getSurface() == null");
            return;
        }

        // We need to save the holder for later use, even when the mCameraDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        mSurfaceHolder = holder;

        // To Set preview for camera ratio
        // The isPreviewSet will be false then update surface view to maintain camera ration in all device
        if (!isPreviewSet) {
            int widthL = width, heightL = height;
            if (mCameraDevice != null) {
                Size size = previewSize;
                if (size != null) {
                    width = size.getWidth();
                    height = size.getHeight();
                }
            } else {
                return;
            }

            // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
//        if (isPortraitMode()) {
            int tmp = width;
            width = height;
            height = tmp;
//        }

            final int layoutWidth = widthL;
            final int layoutHeight = heightL;

            // Computes height and width for potentially doing fit width.
            int childWidth;
            int childHeight;
            int childXOffset = 0;
            int childYOffset = 0;
            float widthRatio = (float) layoutWidth / (float) width;
            float heightRatio = (float) layoutHeight / (float) height;

            // To fill the view with the camera preview, while also preserving the correct aspect ratio,
            // it is usually necessary to slightly oversize the child and to crop off portions along one
            // of the dimensions.  We scale up based on the dimension requiring the most correction, and
            // compute a crop offset for the other dimension.
            if (widthRatio > heightRatio) {
                childWidth = layoutWidth;
                childHeight = (int) ((float) height * widthRatio);
                childYOffset = (childHeight - layoutHeight) / 2;
            } else {
                childWidth = (int) ((float) width * heightRatio);
                childHeight = layoutHeight;
                childXOffset = (childWidth - layoutWidth) / 2;
            }

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.setMargins(-1 * childXOffset, -1 * childYOffset, -1 * childXOffset, -1 * childYOffset);
            Log.e(TAG, "surfaceChanged: "+childXOffset+","+childYOffset );
            preview.setLayoutParams(params);
            isPreviewSet = true;
        }

        // The mCameraDevice will be null if it fails to connect to the camera
        // hardware. In this case we will show a dialog and then finish the
        // activity, so it's OK to ignore it.
        if (mCameraDevice == null)
            return;

        // Sometimes surfaceChanged is called after onPause or before onResume.
        // Ignore it.
        if (mPausing || isFinishing())
            return;

        // Set preview display if the surface is being created. Preview was
        // already started. Also restart the preview if display rotation has
        // changed. Sometimes this happens when the device is held in portrait
        // and camera app is opened. Rotation animation takes some time and
        // display rotation in onCreate may not be what we want.
        if (mCameraState == PREVIEW_STOPPED) {
            startPreview();
        } else {
            if (Util.getDisplayRotation(this) != mDisplayRotation) {
                setDisplayOrientation();
            }
            if (holder.isCreating()) {
                // Set preview display if the surface is being created and
                // preview
                // was already started. That means preview display was set to
                // null
                // and we need to set it now.
                setPreviewDisplay(holder);
            }
        }

        // If first time initialization is not finished, send a message to do
        // it later. We want to finish surfaceChanged as soon as possible to let
        // user see preview first.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (mCameraDevice != null) {
                Camera.Parameters p = mCameraDevice.getParameters();
                List<String> focusModes = p.getSupportedFocusModes();

//                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
//                    mCameraDevice.autoFocus(new AutoFocusCallback());
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        stopPreview();
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onShutter() {
        // TODO Auto-generated method stub
    }

    @Override
    public void onPreviewFrame(final byte[] data, Camera camera) {
        // TODO Auto-generated method stub
        if (LOGV) {
            Log.e(TAG, "onPreviewFrame mPausing=" + mPausing + ", mCameraState=" + mCameraState);
        }

        if (mPausing) {
//			mCardScanner.isRecognizing = false;
            return;
        }

        if (mCameraState != IDLE) {
            mCameraDevice.setOneShotPreviewCallback(CameraActivity.this);
            return;
        } else {
//			mCardScanner.isRecognizing = true;
        }
        // generate jpeg image.
        final int width = camera.getParameters().getPreviewSize().width;
        final int height = camera.getParameters().getPreviewSize().height;
        final int format = camera.getParameters().getPreviewFormat();
//        Display display = getWindowManager().getDefaultDisplay();
//        Point size = new Point();
//        display.getSize(size);
//        final int deviceWidth = size.x;
//        final int deviceHeight = size.y;

//            Log.e("SensorsActivity", "onPreviewFrame: " );
        Thread recogThread = new Thread(new Runnable() {
            int ret;
            ImageOpencv frameData;
            int faceret = 0; // detecting face return value
            Bitmap bmCard = null;

            //
            @Override
            public void run() {

                if (bmCard != null && !bmCard.isRecycled()) {
                    bmCard.recycle();
                }
                int i = openCvHelper.doCheckFrame(data, width, height);
                if (i > 0) {
                    YuvImage temp = new YuvImage(data, format, width, height, null);
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    temp.compressToJpeg(new Rect(0, 0, temp.getWidth(), temp.getHeight()), 100, os);
                    temp = null;
                    //getting original bitmap of scan result
                    Bitmap bmp1 = BitmapFactory.decodeByteArray(os.toByteArray(), 0, os.toByteArray().length);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(mDisplayOrientation);

                    Bitmap cropped = Bitmap.createBitmap(bmp1, 0, 0, bmp1.getWidth(), bmp1.getHeight(), matrix, true);
                    matrix.reset();

                    int totalHeight = dm.heightPixels - titleBarHeight;
                    Point centerOfCanvas = new Point(dm.widthPixels / 2, totalHeight / 2);
                    int left = centerOfCanvas.x - (rectW / 2);
                    int top = centerOfCanvas.y - (rectH / 2);
                    int right = centerOfCanvas.x + (rectW / 2);
                    int bottom = centerOfCanvas.y + (rectH / 2);
                    Rect frameRect = new Rect(left, top, right, bottom);
                    float widthScaleFactor = (float) dm.widthPixels / (float) height;
                    float heightScaleFactor = (float) (totalHeight) / (float) width;
                    frameRect.left = (int) (frameRect.left / widthScaleFactor);
                    frameRect.top = (int) (frameRect.top / heightScaleFactor);
                    frameRect.right = (int) (frameRect.right / widthScaleFactor);
                    frameRect.bottom = (int) (frameRect.bottom / heightScaleFactor);
                    Rect finalrect = new Rect((int) (frameRect.left), (int) (frameRect.top), (int) (frameRect.right), (int) (frameRect.bottom));
                    try {
                        if (finalrect.left >= 0 && finalrect.top >= 0 && finalrect.width() > 0 && finalrect.height() > 0) {
                            bmCard = Bitmap.createBitmap(cropped, finalrect.left, finalrect.top, finalrect.width(), finalrect.height());//memory leak
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        bmCard = null;
                    }

                    bmp1.recycle();
                    cropped.recycle();

//
//                    if (isfront) {
//                        File file = new File("/storage/emulated/0/DCIM/imageFace.jpg"); // front card
//                        bmCard = BitmapFactory.decodeFile(file.getAbsolutePath());
//                    }else {
//                        File file = new File("/storage/emulated/0/DCIM/imageFace (1).jpg"); // back card
//                        bmCard = BitmapFactory.decodeFile(file.getAbsolutePath());
//                    }

                    _mutex.lock();

                    if (bmCard != null) {

                        if (!isfront) {
                            if (mRecCnt > 3) {
                                frameData = openCvHelper.nativeCheckCardIsInFrame(CameraActivity.this, bmCard.copy(Bitmap.Config.ARGB_8888, false), doblurcheck);
                                if (frameData != null && frameData.accuraOCR.equals(OpenCvHelper.AccuraOCR.SUCCESS)) {
                                    onUpdateProcess(openCvHelper.getErrorMessage("3"));
                                        Bitmap docBmp = bmCard.copy(Bitmap.Config.ARGB_8888, false);
                                        int width = docBmp.getWidth();
                                        int height = docBmp.getHeight();
                                        float ratio = width / (float) height;

        //                                Log.v("Pictures", "Width and height are " + width + "--" + height);

                                        if (width > height) {
                                            // landscape

                                            if (ratio > 1.8 || ratio < 1.6) {
                                                height = (int) (width / 1.69);
                                            }
                                        }
                                        docBmp = Bitmap.createScaledBitmap(docBmp, width, height, false);
                                    ret = mCardScanner.doRunData(docBmp, mDisplayRotation, g_recogResult);

                                    if (ret > 0) {
                                        gotmrz = 0;
                                    }
                                }
                            }
                            mRecCnt++;
                        } else {
//                                //<editor-fold desc="Bright Image Face Image in flutter">
//                                // bright front image for make face more bright
//                                Bitmap bitmap = brightImage(bmCard);
//                                frameData = openCvHelper.nativeCheckCardIsInFrame(CameraActivity.this, bitmap, doblurcheck);
//                                //</editor-fold>
                            frameData = openCvHelper.nativeCheckCardIsInFrame(CameraActivity.this, bmCard, doblurcheck);
                        }
                    }

                    _mutex.unlock();

                    if (bmCard != null) {
                        CameraActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isDone) {
                                    if (!isfront && gotmrz == 0 && (frameData.finalData == null || !frameData.accuraOCR.equals(OpenCvHelper.AccuraOCR.SUCCESS))) {
                                        Bitmap docBmp = bmCard;
                                        if (AfterMapping(ocrData.getBackData()/*mrzData*/, docBmp.copy(Bitmap.Config.ARGB_8888, false))) {
                                            try {
                                                docBmp.recycle();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            showResultActivity();
                                        } else {
                                            Log.d(TAG, "failed");
                                            try {
                                                bmCard.recycle();
                                                docBmp.recycle();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }

                                            mHandler.sendMessageDelayed(
                                                    mHandler.obtainMessage(TRIGER_RESTART_RECOG),
                                                    TRIGER_RESTART_RECOG_DELAY);
                                        }
                                    }
                                    if (frameData != null && frameData.accuraOCR.equals(OpenCvHelper.AccuraOCR.SUCCESS)) {
                                        Bitmap docBmp = bmCard;
                                        if (!frameData.rect.isEmpty() && frameData.rect.contains(",")) {
                                            docBmp = frameData.getBitmap();
                                            if (docBmp == null) {
                                                String[] reststring = frameData.rect.split(",");
                                                if (reststring.length == 4) {
                                                    Rect rect = new Rect(Integer.valueOf(reststring[0]), Integer.valueOf(reststring[1]), Integer.valueOf(reststring[2]), Integer.valueOf(reststring[3]));
                                                    docBmp = Bitmap.createBitmap(docBmp, rect.left, rect.top, rect.width(), rect.height());//memory leak
                                                }
                                            }
                                        }
                                        if (AfterMapping(frameData.finalData, docBmp.copy(Bitmap.Config.ARGB_8888, false))) {
                                            try {
                                                bmCard.recycle();
                                                docBmp.recycle();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            showResultActivity();
                                        } else {
                                            Log.d(TAG, "failed");

                                            try {
                                                docBmp.recycle();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }

                                            mHandler.sendMessageDelayed(
                                                    mHandler.obtainMessage(TRIGER_RESTART_RECOG),
                                                    TRIGER_RESTART_RECOG_DELAY);
                                        }
                                    } else {
                                        Log.d(TAG, "failed");

                                        if (isDone) {
                                            return;
                                        }

                                        try {
                                            bmCard.recycle();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        mHandler.sendMessageDelayed(
                                                mHandler.obtainMessage(TRIGER_RESTART_RECOG),
                                                TRIGER_RESTART_RECOG_DELAY);
                                    }
                                }else {
                                    bmCard.recycle();
                                }
                            }

                        });
                    } else{
                        mHandler.sendMessageDelayed(
                                mHandler.obtainMessage(TRIGER_RESTART_RECOG),
                                TRIGER_RESTART_RECOG_DELAY);
                    }
                } else if (i==0){
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(TRIGER_RESTART_RECOG),
                            TRIGER_RESTART_RECOG_DELAY);
                }
            }

            private boolean AfterMapping(String[][] result, Bitmap image) {
                if (result == null || isDone) {
                    return false;
                }
                if (isfront) {
                    if (ocrData.getFrontData() == null) {
                        ocrData.setFrontData(result);
                        ocrData.setFrontimage(image, fileCacheDirectory);
                    }
                    if (ocrData.getBackData() == null) {
                        mScanTitle.setText("Now Scan Back Side of " + ocrData.getCardname());
                        flipImage();
                        //set back image templete
                        SetBackTemplete();
                    }
                } else if (isback) {
//                    if (application.getBackData() == null) {
                    ocrData.setBackData(result);
                    ocrData.setBackimage(image, fileCacheDirectory);
//                    }
                    if (ocrData.getFrontData() == null) {
                        mScanTitle.setText("Now Scan Front Side of " + ocrData.getCardname());
                        flipImage();
                        //set back image templete
                        SetFrontTemplete();
                    }
                }
//                try {
//                    image.recycle();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
                if (ocrData.getFrontData() != null && ocrData.getBackData() != null && (gotmrz == 0 /*|| !g_recogResult.lines.equals("")*/)) {
                    return true;
                } else {
                    return false;
                }
            }
        });
        if (!isDone) {
                recogThread.start();
            }

    }

    private Bitmap brightImage(Bitmap bmCard) {
        Mat srcMat = new Mat();// src frame Mat
        Mat outMat= new Mat(); // bright image mat
        float invGamma = 1.0f / 1.5f;
        // convert bitmap to mat
        Utils.bitmapToMat(bmCard,srcMat);

        Mat lookUpTable = new Mat(1, 256, CvType.CV_8U);
        byte[] lookUpTableData = new byte[(int) (lookUpTable.total()*lookUpTable.channels())];
        for (int k = 0; k < lookUpTable.cols(); k++) {
            lookUpTableData[k] = saturate(Math.pow(k / 255.0, invGamma) * 255.0);
        }
        lookUpTable.put(0, 0, lookUpTableData);
        Core.LUT(srcMat, lookUpTable, outMat);
        // convert opencv Mat to bitmap
        Bitmap bmp = Bitmap.createBitmap(outMat.width(), outMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(outMat, bmp);
        return bmp;
    }

    private byte saturate(double val) {
        int iVal = (int) Math.round(val);
        iVal = iVal > 255 ? 255 : (iVal < 0 ? 0 : iVal);
        return (byte) iVal;
    }

    private void drawOverlay() {
        //draw red rectangle frame according to templete image aspect ratio
        float proportion = 0.636666667f;
        int width = (int) ((dm.widthPixels * 5) / (float) 5.6f);
        int height = (int) (width * proportion);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams layoutParams = rel_main.getLayoutParams();
                layoutParams.width = (int) width;
                layoutParams.height = height;
                rel_main.setLayoutParams(layoutParams);
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams layoutParams = rel_right.getLayoutParams();
                layoutParams.height = height;
                rel_right.setLayoutParams(layoutParams);

            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams layoutParams = rel_left.getLayoutParams();
                layoutParams.height = height;
                rel_left.setLayoutParams(layoutParams);

            }
        });

        rectH = (int) height;
        rectW = (int) width;

    }

    //set first templete from list by default
    private void SetTempleteImage() {
        if (setTemplateFirst("", -1)) {
            drawOverlay();
        }
    }

    private boolean setTemplateFirst(String cardside_, int i) {
//        Mat mat = new Mat();
        PrimaryData data = mCardScanner.setPrimaryData(CameraActivity.this, cardside_, i);
        if (data == null) {
//            mat.release();
            return false;
        }
        cardpos = data.cardPos;
        cardside = data.cardSide;
        if (cardside.contains("Front")) {
            isfront = true;
            isback = false;
        } else {
            isfront = false;
            isback = true;
        }
        return true;
    }

    private void SetBackTemplete() {
        cardside = "Backside";
        g_recogResult.lines = "";
        setTemplateFirst(cardside, -1);
    }

    private void SetFrontTemplete() {
        cardside = "FrontSide";
        g_recogResult.lines = "";
        setTemplateFirst(cardside, -1);
    }

    public void showToast(final String msg) {
        try {
            if (!isFinishing()) {
                if (isfront) {
                    if (msg.contains("Blur detected over face")) {
                        if (!isTouchCalled && mParameters != null) {
                            String focusMode = mParameters.getFocusMode();
                            if (focusMode == null || Camera.Parameters.FOCUS_MODE_INFINITY.equals(focusMode)) {
                                return;
                            }

                            //                    if (e.getAction() == MotionEvent.ACTION_UP) {
                            isTouchCalled = true;
                            autoFocus();
                            //                        setCameraState(IDLE);

                            //                    }

                        }
                    }
                }

                Runnable runnable = new Runnable() {
                    public void run() {
                        mScanMsg.setText(msg);
                    }
                };
                runOnUiThread(runnable);
                drawOverlay();
            }
            //            }
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    ObjectAnimator anim = null;

    //flip the image
    private void flipImage() {
        try {
//            mFlipImage.setVisibility(View.VISIBLE);
            anim = (ObjectAnimator) AnimatorInflater.loadAnimator(mContext, R.animator.flipping);
            anim.setTarget(mFlipImage);
            anim.setDuration(1000);


            Animator.AnimatorListener animatorListener
                    = new Animator.AnimatorListener() {

                public void onAnimationStart(Animator animation) {
                    try {
                        mFlipImage.setVisibility(View.VISIBLE);
                        playEffect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                public void onAnimationRepeat(Animator animation) {

                }

                public void onAnimationEnd(Animator animation) {
                    try {
                        mFlipImage.setVisibility(View.INVISIBLE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                public void onAnimationCancel(Animator animation) {

                }
            };

            anim.addListener(animatorListener);
            anim.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        List<Integer> frameRates = mParameters.getSupportedPreviewFrameRates();
        if (frameRates != null) {
            Integer max = Collections.max(frameRates);
            mParameters.setPreviewFrameRate(max);
        }

        //mParameters.setRecordingHint(false);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters
                .get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersPreference() {

        // Since change scene mode may change supported values,

        //mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//        mModeView.setText(R.string.preview_mode);

        int camOri = CameraHolder.instance().getCameraInfo()[mCameraId].orientation;
        // Set the preview frame aspect ratio according to the picture size.
        Camera.Size size = mParameters.getPictureSize();
        double aspectWtoH = 0.0;
        if ((camOri == 0 || camOri == 180) && size.height > size.width) {
            aspectWtoH = (double) size.height / size.width;
        } else {
            aspectWtoH = (double) size.width / size.height;
        }

        if (LOGV)
            Log.e(TAG, "picture width=" + size.width + ", height=" + size.height);

        // Set a preview size that is closest to the viewfinder height and has the right aspect ratio.
        List<Camera.Size> sizes = mParameters.getSupportedPreviewSizes();
        Camera.Size optimalSize;
        //if (mode == SettingsActivity.CAPTURE_MODE)
        //	optimalSize = Util.getOptimalPreviewSize(this, sizes, aspectWtoH);
        //else
        {
            int requiredArea = mPreviewWidth * mPreviewHeight;

            //optimalSize = Util.getOptimalPreviewSize(this, sizes, aspectWtoH);
            optimalSize = Util.getOptimalPreviewSizeByArea(sizes, requiredArea);
        }

        // Camera.Size optimalSize = Util.getMaxPreviewSize(sizes, camOri);
        Camera.Size original = mParameters.getPreviewSize();

        if (LOGV) Log.i(TAG, " Sensor[" + mCameraId + "]'s orientation is " + camOri);
        if (!original.equals(optimalSize)) {
            if (camOri == 0 || camOri == 180) {
                mParameters.setPreviewSize(optimalSize.height, optimalSize.width);
            } else {
                mParameters.setPreviewSize(optimalSize.width, optimalSize.height);
            }

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get lastest values

            if (mCameraDevice != null) {
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        }
        if (LOGV)
            Log.e(TAG, "Preview size is " + optimalSize.width + "x"
                    + optimalSize.height);
        previewSize = new Size(optimalSize.width,optimalSize.height);

        String previewSize = "";
        previewSize = "[" + optimalSize.width + "x" + optimalSize.height + "]";
//        mPreviewSizeView.setText(previewSize);

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(
                mCameraId, CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        //if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode))
        {
            if (mParameters != null) {
                // Set white balance parameter.
                String whiteBalance = "auto";
                if (isSupported(whiteBalance,
                        mParameters.getSupportedWhiteBalance())) {
                    mParameters.setWhiteBalance(whiteBalance);
                }

                String focusMode = mFocusManager.getFocusMode();
                mParameters.setFocusMode(focusMode);

                // Set exposure compensation
                int value = 0;
                int max = mParameters.getMaxExposureCompensation();
                int min = mParameters.getMinExposureCompensation();
                if (value >= min && value <= max) {
                    mParameters.setExposureCompensation(value);
                } else {
                    if (LOGV) Log.w(TAG, "invalid exposure range: " + value);
                }
            }
        }


        if (mParameters != null) {
            // Set flash mode.
            String flashMode = "off";
            List<String> supportedFlash = mParameters.getSupportedFlashModes();
            if (isSupported(flashMode, supportedFlash)) {
                mParameters.setFlashMode(flashMode);
            }

            if (LOGV) Log.e(TAG, "focusMode=" + mParameters.getFocusMode());
        }

    }

    public void GetCameraResolution() {
        if (mParameters != null && !isBlurSet) {
            isBlurSet = true;
            List sizes = mParameters.getSupportedPictureSizes();
            Camera.Size result = null;

            ArrayList<Integer> arrayListForWidth = new ArrayList<Integer>();
            ArrayList<Integer> arrayListForHeight = new ArrayList<Integer>();

            for (int i = 0; i < sizes.size(); i++) {
                result = (Camera.Size) sizes.get(i);
                arrayListForWidth.add(result.width);
                arrayListForHeight.add(result.height);
            }
            if (arrayListForWidth.size() != 0 && arrayListForHeight.size() != 0) {
                // Gives Maximum Height
                int resolution = ((Collections.max(arrayListForWidth)) * (Collections.max(arrayListForHeight))) / 1024000;
                if (resolution >= 10) {
                    doblurcheck = true;
                } else {
                    doblurcheck = false;
                }
            }

            arrayListForWidth.clear();
            arrayListForHeight.clear();
        }
    }


    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        if (mCameraDevice != null) {
            mParameters = mCameraDevice.getParameters();

            if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
                updateCameraParametersInitialize();
            }


            if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
                updateCameraParametersPreference();
                mIsAutoFocusCallback = false;
            }

            if (mParameters != null) {
                mCameraDevice.setParameters(mParameters);
                GetCameraResolution();
            }
        }
    }

    public void autoFocus() {

        if (mCameraDevice != null) {
            Camera.Parameters p = mCameraDevice.getParameters();
            List<String> focusModes = p.getSupportedFocusModes();

            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                if (FocusManager.isSupported(Parameters.FOCUS_MODE_AUTO, mParameters.getSupportedFocusModes())) {
                    mParameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
                    if (mCameraDevice != null) {
                        mCameraDevice.setParameters(mParameters);
                        mCameraDevice.autoFocus(mAutoFocusCallback);
                        setCameraState(FOCUSING);
                        isTouchCalled = false;
                    }
                }
            } else {
                isTouchCalled = false;
            }
        }

    }

    @Override
    public void cancelAutoFocus() {
        // TODO Auto-generated method stub
        mCameraDevice.cancelAutoFocus();
        if (mCameraState != SELFTIMER_COUNTING
                && mCameraState != SNAPSHOT_IN_PROGRESS) {
            setCameraState(IDLE);
        }
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
        isTouchCalled = false;
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot then ignore.
        if (mCameraState == SNAPSHOT_IN_PROGRESS || mCameraDevice == null) {
            return false;
        }
        setCameraState(SNAPSHOT_IN_PROGRESS);

        return true;
    }

    @Override
    public void setFocusParameters() {
        // TODO Auto-generated method stub
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    @Override
    public void playSound(int soundId) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
//        if (mPausing || mCameraDevice == null || !mFirstTimeInitialized
//                || mCameraState == SNAPSHOT_IN_PROGRESS
//                || mCameraState == PREVIEW_STOPPED
//                || mCameraState == SAVING_PICTURES) {
//            return false;
//        }
        if (!isTouchCalled) {
            String focusMode = mParameters.getFocusMode();
            if (focusMode == null || Parameters.FOCUS_MODE_INFINITY.equals(focusMode)) {
                return false;
            }

            if (e.getAction() == MotionEvent.ACTION_UP) {
                isTouchCalled = true;
                autoFocus();
            }

        }

        //
        //return mFocusManager.onTouch(e);

        return true;
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(
                        SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    private boolean isCameraIdle() {
        return (mCameraState == IDLE || mFocusManager.isFocusCompleted());
    }

    void playEffect() {
        if (audioManager != null)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), 0);
        mediaPlayer.start();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer1) {
                //mediaPlayer.stop();
                //mediaPlayer.release();
            }
        });
    }

    //requesting the camera permission
    public void requestCameraPermission() {
        int currentapiVersion = Build.VERSION.SDK_INT;
        if (currentapiVersion >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) &&
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            1);

                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            1);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        }
        switch (requestCode) {
            case 1:
                // If request is cancelled, the result arrays are empty.
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Start your camera handling here
                    try {
                        mCameraDevice = Util.openCamera(CameraActivity.this, mCameraId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
                    Toast.makeText(this, "You declined to allow the app to access your camera", Toast.LENGTH_SHORT).show();
                }
        }
    }

    String newMessage = "";
    @Override
    public void onUpdateProcess(final String s) {
//        showToast(s);
//        if (!newMessage.equals(s)) {
//            newMessage = s;
//            Runnable runnable = new Runnable() {
//                public void run() {
//                    if (!isFinishing()) {
//                        mScanMsg.setText(s);
//                    }
//                }
//            };
//            runOnUiThread(runnable);
//        }

        if (!s.isEmpty()) {
            newMessage = s;
            Runnable runnable1 = new Runnable() {
                public void run() {
                    if (!isFinishing()) {
                        mScanMsg.setText(s);
                    }
                }
            };
            runOnUiThread(runnable1);
            if (!s.contains("Processing")) {
                final Runnable runnable = () -> {
                    try {
                        if (!s.contains("lighting"))
                            if (newMessage.equals(s) || !s.contains("Processing")) {
                                mScanMsg.setText("");
                            }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                Runnable run = () -> new Handler().postDelayed(runnable, 1000);
                if (!isFinishing()) {
                    runOnUiThread(run);
                }
            }
        }
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLEAR_SCREEN_DELAY: {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }
                case FIRST_TIME_INIT: {
                    initializeFirstTime();
                    break;
                }

//                case SET_CAMERA_PARAMETERS_WHEN_IDLE: {
//                    setCameraParametersWhenIdle(0);
//                    break;
//                }

                case TRIGER_RESTART_RECOG:
                    if (!mPausing && mCameraDevice != null) {
                        mCameraDevice.setOneShotPreviewCallback(CameraActivity.this);
                    }
                    // clearNumberAreaAndResult();
                    break;
            }
        }
    }

    private final class AutoFocusCallback implements
            Camera.AutoFocusCallback {
        public void onAutoFocus(boolean focused, Camera camera) {
            if (mPausing)
                return;

            if (mCameraState == FOCUSING) {
                setCameraState(IDLE);
            }
            mFocusManager.onAutoFocus(focused);
            mIsAutoFocusCallback = true;

            String focusMode = mFocusManager.getFocusMode();
            mParameters.setFocusMode(focusMode);
            mCameraDevice.setParameters(mParameters);
            isTouchCalled = false;
//            autoFocus();
        }
    }

    public class CameraErrorCallback implements Camera.ErrorCallback {
        private static final String TAG = "CameraErrorCallback";

        public void onError(int error, Camera camera) {
            if (LOGV) Log.e(TAG, "Got camera error callback. error=" + error);
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                // We are not sure about the current state of the app (in preview or
                // snapshot or recording). Closing the app is better than creating a
                // new Camera object.
                throw new RuntimeException("Media server died.");
            }
        }
    }


}

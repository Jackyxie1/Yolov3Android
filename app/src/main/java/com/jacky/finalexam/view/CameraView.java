/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jacky.finalexam.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import com.jacky.finalexam.App;
import com.jacky.finalexam.jni.Yolo;
import com.jacky.finalexam.utils.Util;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
public class CameraView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "AutoFitSurfaceView";

    private SurfaceHolder mHolder;
    private Canvas mCanvas;
    private Paint paint;
    private Bitmap mBitmap;
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private Type.Builder yuvType, rgbaType;
    private Allocation in, out;

    private boolean isLoad = false;
    private int[] dims = {1, 3, 416, 416};
    private ArrayList<String> resultLabel = new ArrayList<>();
    private com.jacky.finalexam.jni.Yolo Yolo = new Yolo();

    private float mWidth = 0;
    private float mHeight = 0;

    private void initYolo() throws IOException {
        boolean isLoad = false;


        String paramPath = Util.getPathFromAssets(App.getContext(), "yolov3-tiny.param");
        String binPath = Util.getPathFromAssets(App.getContext(), "yolov3-tiny.bin");

        isLoad = Yolo.Init(paramPath, binPath);
        Log.d(TAG, "load model success?:" + isLoad);
    }

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Log.i(TAG, "AutoFitSurfaceView struct");
        initParams(context);
        try {
            initYolo();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Util.loadLabels(resultLabel, App.getContext());
    }

    private void initParams(Context context) {
        mHolder = getHolder();
        mHolder.addCallback(this);
        paint = new Paint();
        paint.setColor(Color.WHITE);
        setFocusable(true);
        rs = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        Log.i(TAG, "setAspectRatio " + width + "X" + height);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated ");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged " + width + "X" + height);
        mWidth = width;
        mHeight = height;

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed ");
    }

    private void drawRect(Rect rect, int color, String string) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setTextSize(24f);
        paint.setStrokeWidth(3.6f);

        mCanvas.drawRect(rect, paint);
        paint.setStyle(Paint.Style.FILL);
        mCanvas.drawText(string, rect.left + 4, rect.top + 14, paint);
    }

    static private int class_color[] = {
            Color.YELLOW, Color.GREEN, Color.BLUE, Color.YELLOW, Color.BLUE,
            Color.YELLOW, Color.BLUE, Color.BLUE, Color.YELLOW, Color.YELLOW,
            Color.YELLOW, Color.YELLOW, Color.YELLOW, Color.YELLOW, Color.YELLOW,
            Color.RED, Color.YELLOW, Color.YELLOW, Color.YELLOW, Color.YELLOW,
            Color.YELLOW,
    };


    public void drawDetect(float[] data, int width, int height, int rolatedeg) {
        int obj = 0;
        int num = data.length / 6;
        int t, x, y, xe, ye;
        float[][] finalArray = Util.twoArray(data);

        for (obj = 0; obj < num; obj++) {
            t = (int) finalArray[obj][1];
            x = (int) (finalArray[obj][2] * width);
            y = (int) (finalArray[obj][3] * height);
            xe = (int) (finalArray[obj][4] * width);
            ye = (int) (finalArray[obj][5] * height);

            if (x < 0) {
                x = 0;
            }
            if (y < 0) {
                y = 0;
            }
            if (xe > width) {
                xe = width;
            }
            if (ye > height) {
                ye = height;
            }

            drawRect(new Rect(x, y, xe, ye), class_color[t], resultLabel.get((int) finalArray[obj][0]));
        }

    }

    private float[] result;
    private boolean isRunning;

    public void draw(byte[] data, int width, int height, int rolatedeg) {
        long startTime, endTime;
        Log.d(TAG, "draw " + data.length + " " + width + "X" + height);
        if (data != null) {
            Log.d(TAG, "-------------start----------------");
            startTime = System.currentTimeMillis();
//            final YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);
//            ByteArrayOutputStream stream = new ByteArrayOutputStream();
//            image.compressToJpeg(new Rect(0, 0, width, height), 100, stream);

            mBitmap = yuvToBitmap(data, width, height);
//            mBitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
//            Bitmap rgbout = mBitmap.copy(Bitmap.Config.ARGB_8888, true);
            endTime = System.currentTimeMillis();
            Log.d(TAG, "相机返回帧数据处理为bitmap消耗的时间为： " + (endTime - startTime) + " ms");
            Log.d(TAG, "----------------end------------------");
            if (mBitmap == null) {
                Log.d(TAG, "data data is to bitmap error");
                return;
            }

            try {
                mCanvas = mHolder.lockCanvas();
                mWidth = mCanvas.getWidth();
                mHeight = mCanvas.getHeight();
                Log.d(TAG, "canvas size is " + mWidth + " " + mHeight);
                float scaleWidth = mWidth / width;
                float scaleHeight = mHeight / height;
                Log.d(TAG, "scale size is " + scaleWidth + " " + scaleHeight);


                Matrix matrix = new Matrix();
                matrix.setScale(scaleWidth, scaleHeight);
                mCanvas.drawBitmap(mBitmap, matrix, paint);
                final Bitmap input_bmp = Bitmap.createScaledBitmap(mBitmap, dims[2], dims[3], false);


                if (!isRunning) {
                    isRunning = true;
                    new Thread(new Runnable() {
                        public void run() {
                            if (input_bmp != null)
                                result = Yolo.Detect(input_bmp);
                            isRunning = false;
                        }
                    }).start();
                }
                if (null != result) {
                    drawDetect(result, (int) mWidth, (int) mHeight, rolatedeg);
                }
            } catch (Exception e) {
                Log.d(TAG, "e=" + e);
                mHolder.unlockCanvasAndPost(mCanvas);
                return;
            }
            mHolder.unlockCanvasAndPost(mCanvas);
        } else {
            Log.d(TAG, "data data is null");
            return;
        }
    }

    public Bitmap yuvToBitmap(byte[] yuv, int width, int height) {
        if (yuvType == null) {
            yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuv.length);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);//分配内存
            rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(yuv);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        Bitmap rgba = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(rgba);
        return rgba;
    }
}

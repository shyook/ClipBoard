/*
 * Copyright 2013 Tristan Waddington
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.example.clipboardmonitor.service;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Date;

/**
 * Monitors the {@link ClipboardManager} for changes and logs the text to a file.
 */
public class ClipboardMonitorService extends Service {
    private static final String TAG = "ClipboardManager";
    private static final String FILENAME = "clipboard-history.txt";

    private File mHistoryFile;
    private ExecutorService mThreadPool = Executors.newSingleThreadExecutor();
    private ClipboardManager mClipboardManager;

    private TextView mPopupView;                            //항상 보이게 할 뷰
    private WindowManager.LayoutParams mParams;  //layout params 객체. 뷰의 위치 및 크기
    private WindowManager mWindowManager;          //윈도우 매니저

    private float mTouchX, mTouchY;
    private int mViewX, mViewY;
    private boolean isMove = false;

    private Context mContext;

    private TimerTask mTask;
    private Timer mTimer;

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        // TODO: Show an ongoing notification when this service is running.
        mHistoryFile = new File(getExternalFilesDir(null), FILENAME);
        mClipboardManager =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        mClipboardManager.addPrimaryClipChangedListener(
                mOnPrimaryClipChangedListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mClipboardManager != null) {
            mClipboardManager.removePrimaryClipChangedListener(mOnPrimaryClipChangedListener);
        }

        if(mWindowManager != null) {        //서비스 종료시 뷰 제거. *중요 : 뷰를 꼭 제거 해야함.
            if(mPopupView != null) {
                mWindowManager.removeView(mPopupView);
            }
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private ClipboardManager.OnPrimaryClipChangedListener mOnPrimaryClipChangedListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
            Log.d(TAG, "onPrimaryClipChanged");
            ClipData clip = mClipboardManager.getPrimaryClip();
            mThreadPool.execute(new WriteHistoryRunnable(clip.getItemAt(0).getText()));
        }
    };

    private void onTimerSetting() {
        mTask = new TimerTask() {
            @Override
            public void run() {
                if (mPopupView != null) {
                    handler.sendEmptyMessage(1);
                }
            }
        };
        mTimer = new Timer();
        mTimer.schedule(mTask, 3000);
    }

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == 0){   // Message id 가 0 이면
                initView(msg.obj.toString());
                onTimerSetting();
            } else if (msg.what == 1) {
                destoryView();
            }
        }
    };

    private void destoryView() {
        mPopupView.setVisibility(View.GONE);
        mPopupView = null;
        mCount = 0;
    }

    private class WriteHistoryRunnable implements Runnable {
        private final Date mNow;
        private final CharSequence mTextToWrite;

        public WriteHistoryRunnable(CharSequence text) {
            mNow = new Date(System.currentTimeMillis());
            mTextToWrite = text;
        }

        @Override
        public void run() {
            if (TextUtils.isEmpty(mTextToWrite)) {
                // Don't write empty text to the file
                return;
            }
            if (isExternalStorageWritable()) {
                    Log.i(TAG, "Writing new clip to history:");
                    Log.i(TAG, mTextToWrite.toString());
//                    BufferedWriter writer =
//                            new BufferedWriter(new FileWriter(mHistoryFile, true));
//                    writer.write(String.format("[%s]: ", mNow.toString()));
//                    writer.write(mTextToWrite.toString());
//                    writer.newLine();
//                    writer.close();
                Message msg = new Message();
                msg.what = 0;
                msg.obj = new String(mTextToWrite.toString());
                handler.sendMessage(msg);
            } else {
                Log.w(TAG, "External storage is not writable!");
            }
        }
    }

    private void initView(String strCopy) {
        mPopupView = new TextView(mContext);                                         //뷰 생성
        mPopupView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); //텍스트 크기 18sp
        mPopupView.setText(strCopy);                        //텍스트 설정
        mPopupView.setTextColor(Color.BLUE);                                  //글자 색상
        mPopupView.setBackgroundColor(Color.argb(127, 0, 255, 255)); //텍스트뷰 배경 색
        mPopupView.setOnTouchListener(mViewTouchListener);              //팝업뷰에 터치 리스너 등록

        //최상위 윈도우에 넣기 위한 설정
        mParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,//항상 최 상위. 터치 이벤트 받을 수 있음.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  //포커스를 가지지 않음
                PixelFormat.TRANSLUCENT);                                        //투명
        mParams.gravity = Gravity.LEFT | Gravity.BOTTOM;                   //왼쪽 상단에 위치하게 함.

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);  //윈도우 매니저
        mWindowManager.addView(mPopupView, mParams);      //윈도우에 뷰 넣기. permission 필요.
    }

    private static int mCount = 0;
    private View.OnTouchListener mViewTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN:
//                    isMove = false;
//
//                    mTouchX = event.getRawX();
//                    mTouchY = event.getRawY();
//                    mViewX = mParams.x;
//                    mViewY = mParams.y;
//
//                    break;

                case MotionEvent.ACTION_UP:
                    mCount++;
                        Toast.makeText(getApplicationContext(), "터치됨",
                                Toast.LENGTH_SHORT).show();

                        Log.i("shyook", "터치 : " + mCount);

                    break;

//                case MotionEvent.ACTION_MOVE:
//                    isMove = true;
//
//                    int x = (int) (event.getRawX() - mTouchX);
//                    int y = (int) (event.getRawY() - mTouchY);
//
//                    final int num = 5;
//                    if ((x > -num && x < num) && (y > -num && y < num)) {
//                        isMove = false;
//                        break;
//                    }
//
//                    /**
//                     * mParams.gravity에 따른 부호 변경
//                     *
//                     * LEFT : x가 +
//                     *
//                     * RIGHT : x가 -
//                     *
//                     * TOP : y가 +
//                     *
//                     * BOTTOM : y가 -
//                     */
//                    mParams.x = mViewX + x;
//                    mParams.y = mViewY + y;
//
//                    mWindowManager.updateViewLayout(mPopupView, mParams);
//
//                    break;
            }

            return true;
        }
    };
}

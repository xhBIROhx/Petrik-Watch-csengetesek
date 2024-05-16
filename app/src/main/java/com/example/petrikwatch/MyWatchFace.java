package com.example.petrikwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.Calendar;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;


/**
 * Created by paulruiz on 3/25/15.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    @Override
    public Engine onCreateEngine() {
        return new WatchFaceEngine();
    }

    public static int[][] parseJsonToIntArray(JSONArray jsonArray) throws JSONException {
        int[][] result = new int[jsonArray.length()][];

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONArray pair = jsonArray.getJSONArray(i);
            int[] intPair = new int[2];
            intPair[0] = pair.getInt(0);
            intPair[1] = pair.getInt(1);
            result[i] = intPair;
        }

        return result;
    }

    private class WatchFaceEngine extends Engine {

        //Member variables
        private Typeface WATCH_TEXT_TYPEFACE = Typeface.create( Typeface.SERIF, Typeface.NORMAL );

        private static final int MSG_UPDATE_TIME_ID = 42;
        private static final long DEFAULT_UPDATE_RATE_MS = 1000;
        private long mUpdateRateMs = 1000;

        private Time mDisplayTime;
        private Calendar calendar = Calendar.getInstance();
        private int dayOfWeekIndex;

        private Paint mBackgroundColorPaint;
        private Paint mTextColorPaint;

        private boolean mHasTimeZoneReceiverBeenRegistered = false;
        private boolean mIsInMuteMode;
        private boolean mIsLowBitAmbient;

        private float mXOffset;
        private float mYOffset;
        private float mCenterX;
        private float mCenterY;
        //private float lineHeight = 8f;

        private int LastCycleToday;
        private int FirstCycleToday;
        private JSONArray CyclesTodayArray;
        private int[][] CycleTimetableToday;

        private int mBackgroundColor = Color.parseColor( "black" );
        private int mTextColor = Color.parseColor( "black" );
        private int mGlobalTimeSeconds;

        private Bitmap mBackgroundBitmap;

        final BroadcastReceiver mTimeZoneBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mDisplayTime.clear( intent.getStringExtra( "time-zone" ) );
                mDisplayTime.setToNow();
            }
        };

        private final Handler mTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch( msg.what ) {
                    case MSG_UPDATE_TIME_ID: {
                        invalidate();
                        if( isVisible() && !isInAmbientMode() ) {
                            long currentTimeMillis = System.currentTimeMillis();
                            long delay = mUpdateRateMs - ( currentTimeMillis % mUpdateRateMs );
                            mTimeHandler.sendEmptyMessageDelayed( MSG_UPDATE_TIME_ID, delay );
                        }
                        break;
                    }
                }
            }
        };

        //Overridden methods
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle( new WatchFaceStyle.Builder( MyWatchFace.this )
                    .setBackgroundVisibility( WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE )
                    .setCardPeekMode( WatchFaceStyle.PEEK_MODE_VARIABLE )
                    .setShowSystemUiTime( false )
                    .build()
            );

            //find create search load analyze etc the file system

            JSONObject data = DataManager.readData(getApplicationContext());
            if (data == null) { // I'll might put this to a true as we'd probly need to change the values quite often
                // Data file doesn't exist, create it
                data = new JSONObject();
                try {
                    String jsonString = "{\n" +
                            "    \"classes\": [\n" +
                            "        [28800,31500],\n" +
                            "        [32100,34800],\n" +
                            "        [35700,38400],\n" +
                            "        [39000,41700],\n" +
                            "        [42300,45000],\n" +
                            "        [46200,48900],\n" +
                            "        [49500,52200],\n" +
                            "        [52500,55200]\n" +
                            "    ],\n" +
                            "    \"days\": [\n" +
                            "        [1,7],\n" +
                            "        [1,7],\n" +
                            "        [1,8],\n" +
                            "        [1,7],\n" +
                            "        [2,6]\n" +
                            "    ]\n" +
                            "}"; // Your JSON string
                    data = new JSONObject(jsonString);
                    DataManager.writeData(getApplicationContext(), data);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("json: "+ data);

            dayOfWeekIndex = calendar.get(Calendar.DAY_OF_WEEK); // 1-sun, 7-sat; 2-mon, 6-fry, must change what is this format!
            //if (dayOfWeekIndex == 1) {dayOfWeekIndex = 7;}
            dayOfWeekIndex-=2; //5 and -1 means weekend
            try {
                CyclesTodayArray = data.getJSONArray("days").getJSONArray(dayOfWeekIndex);
            } catch (JSONException e) {
                //we can assume because it's the weekends
                LastCycleToday = -1;
                FirstCycleToday = -1;
                try { CyclesTodayArray = new JSONArray("[nuh,uh]");
                } catch (JSONException jsonException) {
                    //brother, this is hard coded
                    int a = 1/0;
                }
            } finally {
                //it's weekdays
                try {
                    FirstCycleToday = CyclesTodayArray.getInt(0)-1;
                    LastCycleToday = CyclesTodayArray.getInt(1)-1; //index
                } catch (JSONException jsonException) {
                    //will never happen xd but in case it does
                    int a = 1/0;
                }
                try {
                    CycleTimetableToday = parseJsonToIntArray(data.getJSONArray("classes"));
                    if (LastCycleToday < CyclesTodayArray.length()) {
                        for (int i=CyclesTodayArray.length(); i>LastCycleToday ;i--) {
                            CyclesTodayArray.remove(i);
                        }
                    }
                    if (FirstCycleToday > 0) {
                        for (int i=0; i<FirstCycleToday ;i++) {
                            CyclesTodayArray.remove(i);
                        }
                    }
                } catch (JSONException e) {
                    //will also most likely never happen
                    int a = 1/0;
                }
            }

            //--
            initBackground();
            initDisplayText();

            mDisplayTime = new Time();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            mTextColorPaint.setTextSize(width/8f);

            super.onSurfaceChanged(holder, format, width, height);
            if (mBackgroundBitmap != null) {
                mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap, width, height, true);
            }
        }

        @Override
        public void onDestroy() {
            mTimeHandler.removeMessages( MSG_UPDATE_TIME_ID );
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged( boolean visible ) {
            super.onVisibilityChanged(visible);

            if( visible ) {
                if( !mHasTimeZoneReceiverBeenRegistered ) {

                    IntentFilter filter = new IntentFilter( Intent.ACTION_TIMEZONE_CHANGED );
                    MyWatchFace.this.registerReceiver( mTimeZoneBroadcastReceiver, filter );

                    mHasTimeZoneReceiverBeenRegistered = true;
                }

                mDisplayTime.clear( TimeZone.getDefault().getID() );
                mDisplayTime.setToNow();
            } else {
                if( mHasTimeZoneReceiverBeenRegistered ) {
                    MyWatchFace.this.unregisterReceiver( mTimeZoneBroadcastReceiver );
                    mHasTimeZoneReceiverBeenRegistered = false;
                }
            }

            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            mYOffset = getResources().getDimension( R.dimen.y_offset );

            if( insets.isRound() ) {
                mXOffset = getResources().getDimension( R.dimen.x_offset_round );
            } else {
                mXOffset = getResources().getDimension( R.dimen.x_offset_square );
            }
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            if( properties.getBoolean( PROPERTY_BURN_IN_PROTECTION, false ) ) {
                mIsLowBitAmbient = properties.getBoolean( PROPERTY_LOW_BIT_AMBIENT, false );
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if( inAmbientMode ) {
                mTextColorPaint.setColor( Color.parseColor( "black" ) );
            } else {
                mTextColorPaint.setColor( Color.parseColor( "black" ) );
            }

            if( mIsLowBitAmbient ) {
                mTextColorPaint.setAntiAlias( !inAmbientMode );
            }

            invalidate();
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean isDeviceMuted = ( interruptionFilter == android.support.wearable.watchface.WatchFaceService.INTERRUPTION_FILTER_NONE );
            if( isDeviceMuted ) {
                mUpdateRateMs = TimeUnit.MINUTES.toMillis( 1 );

            } else {
                mUpdateRateMs = DEFAULT_UPDATE_RATE_MS;
            }

            if( mIsInMuteMode != isDeviceMuted ) {
                mIsInMuteMode = isDeviceMuted;
                int alpha = ( isDeviceMuted ) ? 100 : 255;
                mTextColorPaint.setAlpha( alpha );
                invalidate();

            }

            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) { //THIS UPDATES
            super.onDraw(canvas, bounds);

            mDisplayTime.setToNow();

            drawBackground( canvas, bounds );
            drawTimeText( canvas );
        }

        //Utility methods
        private void initBackground() {
            mBackgroundColorPaint = new Paint();
            mBackgroundColorPaint.setColor( mBackgroundColor );
            Resources resources = getResources();
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg);
            if (mBackgroundBitmap == null) {
                System.out.println("Failed to load background image from resources");
            }
        }

        private void initDisplayText() {
            mTextColorPaint = new Paint();
            mTextColorPaint.setColor( mTextColor );
            mTextColorPaint.setTypeface( WATCH_TEXT_TYPEFACE );
            mTextColorPaint.setAntiAlias( true );
            mTextColorPaint.setTextSize( getResources().getDimension( R.dimen.text_size ) );
            mTextColorPaint.setTextAlign(Paint.Align.CENTER);
        }

        private void updateTimer() {
            mTimeHandler.removeMessages( MSG_UPDATE_TIME_ID );
            if( isVisible() && !isInAmbientMode() ) {
                mTimeHandler.sendEmptyMessage( MSG_UPDATE_TIME_ID );
            }
        }

        private void drawBackground( Canvas canvas, Rect bounds ) {
            //canvas.drawRect( 0, 0, bounds.width(), bounds.height(), mBackgroundColorPaint );
            canvas.drawBitmap(mBackgroundBitmap,0,0,mBackgroundColorPaint);
        }

        private void drawTimeText( Canvas canvas ) {
            //HERE HERE
            //PLACEHOLDER
            //RIGHT HERE
            //DO ALL LOGIC HERE BASICALLY
            //EXCEPT BACKGROUND,THAT'S GONNA BE A PAIN

            String[] timeText = GetCorrectTimeText();
            for (int i = 0; i < timeText.length; i++) {
                float y = mCenterY + (i - (timeText.length - 1) / 2.0f) * mTextColorPaint.getTextSize();;
                canvas.drawText(timeText[i], mCenterX, y, mTextColorPaint);
            }
            //canvas.drawText( timeText, mCenterX, mCenterY, mTextColorPaint );
            //canvas.drawText( "O", mCenterX, mCenterY, mTextColorPaint );
        }

        private String[] GetCorrectTimeText(){
            mGlobalTimeSeconds = (mDisplayTime.hour*60+mDisplayTime.minute)*60+mDisplayTime.second;

            if( isInAmbientMode() || mIsInMuteMode ) {
                //timeText += ( mDisplayTime.hour < 12 ) ? "AM" : "PM";
                return new String[]{String.format("%d", mDisplayTime.hour) + ":" + String.format("%02d", mDisplayTime.minute)};
            } else {
                if (FirstCycleToday == -1) {
                    return new String[]{"NINCS MA PETRIK!", "ÉLVEZD AZ ÉLTET!"};
                }else{
                    //We know that we are going to school today
                    //we have a couple of cases

                    //before school
                    if (mGlobalTimeSeconds < CycleTimetableToday[0][0]) {
                        //TIME TILL DOOM
                        return new String[]{"Depression in:",FormatSecondsToTime(CycleTimetableToday[0][0]-mGlobalTimeSeconds)};
                    }
                    //in school
                    else if (mGlobalTimeSeconds < CycleTimetableToday[CycleTimetableToday.length-1][1]) {
                        //ah shit
                        /**
                         * day info:
                         *  cycles left: X
                         *  last cycle
                         * class info:
                         *  cycle ends in: X:X:X
                         *  cycle begins in: X:X:X
                         * school info:
                         *  day ends in: X:X:X
                          */
                        String day = "";
                        String clas= "";
                        String school = "Day ends in: " + FormatSecondsToTime(CycleTimetableToday[CycleTimetableToday.length-1][1]-mGlobalTimeSeconds);

                        for(int i = 0; i<CycleTimetableToday.length; i++) {
                            if (mGlobalTimeSeconds > CycleTimetableToday[i][0] && mGlobalTimeSeconds < CycleTimetableToday[i][1]) {
                                //inside class
                                clas = "Cycle ends in:" + FormatSecondsToTime(CycleTimetableToday[i][1]-mGlobalTimeSeconds);
                                // i => nth class
                                int j = CycleTimetableToday.length-i;
                                if (j==1) {
                                    day = "Last cycle";
                                }else{
                                    day = "Cycles left: " + j;
                                }
                                break;
                            }else if (i != CycleTimetableToday.length-1 && mGlobalTimeSeconds > CycleTimetableToday[i][1] && mGlobalTimeSeconds < CycleTimetableToday[i+1][0]){
                                //during break
                                clas = "Cycle begins in:" + FormatSecondsToTime(CycleTimetableToday[i+1][0]-mGlobalTimeSeconds);
                                int j = CycleTimetableToday.length-i-1;
                                if (j==1) {
                                    day= "One cycle left";
                                }else{
                                    day = "Cycles left: " + j;
                                }
                                break;
                            }
                        }
                        return new String[]{day,clas,school};
                    }

                    //after school
                    //if (mGlobalTimeSeconds > CycleTimetableToday[CycleTimetableToday.length-1][1]) // the same thing but the compiler is more happy
                    else
                    {
                        return new String[]{"it's over",String.format("%d", mDisplayTime.hour) + ":" + String.format("%02d", mDisplayTime.minute) + ":" + String.format("%02d", mDisplayTime.second)};
                    }
                }
            }
        }

        private String FormatSecondsToTime(int sec) {
            int t = sec/60;
            int seconds = sec-t*60;
            int hours = t/60;
            int minutes = t-hours*60;
            return String.format("%d:%02d:%02d",hours,minutes,seconds);
        }
    }

}
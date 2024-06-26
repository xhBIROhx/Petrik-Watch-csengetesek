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
 * Thanks for the base Paul! You saved me hours or days! (on 5/17/24)
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

        private int[][] CycleTimetableToday;
        private boolean OffDay = false;
        private boolean DayStarted = false;

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
            //  I made it this way caz it'd be huge to modify this in a mobile app. But that'd mean coding more java. NO THANK YOU!
            JSONObject data = DataManager.readData(getApplicationContext());
            if (data == null) { // Data file doesn't exist, create it
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
                    //we can't continue with an empty data, so let's just..
                    int a = 1/0;
                }
            }
            System.out.println("json: "+ data);

            GetTimetTable(data); //gives data to CycleTimetableToday if applicable otherwise leaves it null

            //--
            initBackground();
            initDisplayText();

            mDisplayTime = new Time();
        }

        public void GetTimetTable(JSONObject data) {
            dayOfWeekIndex = calendar.get(Calendar.DAY_OF_WEEK); // 1-sun, 7-sat; 2-mon, 6-fry, must change what is this format!
            //if (dayOfWeekIndex == 1) {dayOfWeekIndex = 7;}
            //dayOfWeekIndex -= 2; //5 and -1 means weekend
            JSONArray CyclesTodayArray = new JSONArray(); //will get overwriten 100%
            try {
                CyclesTodayArray = data.getJSONArray("days").getJSONArray(dayOfWeekIndex-2);
            } catch (JSONException e) {
                //we can assume because it's the weekends
                OffDay = true;
            } finally {
                //it's weekdays
                try {
                    int FirstCycleToday = CyclesTodayArray.getInt(0) - 1;
                    int LastCycleToday = CyclesTodayArray.getInt(1);

                    CycleTimetableToday = parseJsonToIntArray(data.getJSONArray("classes"));
                    if (LastCycleToday < CycleTimetableToday.length) {
                        for (int i = CycleTimetableToday.length; i > LastCycleToday; i--) {
                            CycleTimetableToday = removeRow(CycleTimetableToday, CycleTimetableToday.length - 1);
                        }
                    }
                    if (FirstCycleToday > 0) {
                        for (int i = 0; i < FirstCycleToday; i++) {
                            System.out.println("removed " + i);
                            CycleTimetableToday = removeRow(CycleTimetableToday, i);
                        }
                    }
                } catch (JSONException e) {
                    //will also most likely never happen
                    int a = 1 / 0;
                }
            }
        }

        public int[][] removeRow(int[][] array, int rowIndex) {
            if (array == null || rowIndex < 0 || rowIndex >= array.length) {
                throw new IllegalArgumentException("Invalid row index");
            }

            int[][] newArray = new int[array.length - 1][];
            int newArrayIndex = 0;

            for (int i = 0; i < array.length; i++) {
                if (i != rowIndex) {
                    newArray[newArrayIndex++] = array[i];
                }
            }

            return newArray;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mTextColorPaint.setTextSize(width/8f);
            mCenterX = width / 2f;
            mCenterY = (height / 2f) + mTextColorPaint.getTextSize()/2;


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
            String[] timeText = GetCorrectTimeText();
            for (int i = 0; i < timeText.length; i++) {
                float y = mCenterY + (i - (timeText.length - 1) / 2.0f) * mTextColorPaint.getTextSize();;
                canvas.drawText(timeText[i], mCenterX, y, mTextColorPaint);
            }
        }

        private String[] GetCorrectTimeText(){
            mGlobalTimeSeconds = (mDisplayTime.hour*60+mDisplayTime.minute)*60+mDisplayTime.second;

            if( isInAmbientMode() || mIsInMuteMode ) {
                return new String[]{String.format("%d", mDisplayTime.hour) + ":" + String.format("%02d", mDisplayTime.minute)};
            } else {
                if (OffDay) {
                    return new String[]{"NINCS MA PETRIK","!!!"};
                }else{
                    //We know that we are going to school today
                    //we have a couple of cases

                    //after school
                    if (CycleTimetableToday == null) {
                        return new String[]{"it's over",String.format("%d", mDisplayTime.hour) + ":" + String.format("%02d", mDisplayTime.minute) + ":" + String.format("%02d", mDisplayTime.second)};
                    }

                    //before school
                    if (mGlobalTimeSeconds < CycleTimetableToday[0][0] && !DayStarted) {
                        //TIME TILL DOOM
                        //System.out.println(CycleTimetableToday[0][0]);
                        return new String[]{"Depression in:",FormatSecondsToTime(CycleTimetableToday[0][0]-mGlobalTimeSeconds)};
                    }
                    DayStarted = true;

                    //in school
                    while (true) {
                        if (mGlobalTimeSeconds > CycleTimetableToday[0][1]){ // if the first class is over, remove it, so the "first" class becomes the 2nd one
                            if (CycleTimetableToday.length == 1) {// we would have removed all classes
                                CycleTimetableToday = null; //faster remove only index
                                return new String[]{"it's over",String.format("%d", mDisplayTime.hour) + ":" + String.format("%02d", mDisplayTime.minute) + ":" + String.format("%02d", mDisplayTime.second)};
                            }
                            CycleTimetableToday = removeRow(CycleTimetableToday,0);
                            continue;
                        }

                        String[] school = new String[]{"Day ends in:",FormatSecondsToTime(CycleTimetableToday[CycleTimetableToday.length - 1][1] - mGlobalTimeSeconds)};
                        String day;
                        if (CycleTimetableToday.length == 1){
                            day = "Last cycle";
                        }else{
                            day = "Cycles left: " + CycleTimetableToday.length;
                        }

                        String[] clas;
                        //during break
                        if (mGlobalTimeSeconds <= CycleTimetableToday[0][0]) { // break isn't over
                            clas = new String[]{"Cycle Begins in:",FormatSecondsToTime(CycleTimetableToday[0][0]-mGlobalTimeSeconds)};
                        }
                        //during class
                        else /*if (mGlobalTimeSeconds <= CycleTimetableToday[0][1])*/ { //class isn't over
                            clas = new String[]{"Cycle ends in:",FormatSecondsToTime(CycleTimetableToday[0][1]-mGlobalTimeSeconds)};
                        }

                        return new String[]{day,clas[0],clas[1],school[0],school[1]};
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
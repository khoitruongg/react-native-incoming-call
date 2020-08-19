package com.incomingcall;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.net.Uri;
import android.os.Vibrator;
import android.content.Context;
import android.media.MediaPlayer;
import android.provider.Settings;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import android.app.ActivityManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.squareup.picasso.Picasso;

public class UnlockScreenActivity extends AppCompatActivity implements UnlockScreenActivityInterface {

    private static final String TAG = "MessagingService";
    private TextView tvName;
    private ImageView ivAvatar;
    private String uuid = "";
    static boolean active = false;
    private ReadableMap data;
    private Vibrator v = (Vibrator) IncomingCallModule.reactContext.getSystemService(Context.VIBRATOR_SERVICE);
    private long[] pattern = {0, 1000, 800};
    private MediaPlayer player = MediaPlayer.create(IncomingCallModule.reactContext, Settings.System.DEFAULT_RINGTONE_URI);

    @Override
    public void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        active = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_call_incoming);

        tvName = findViewById(R.id.tvName);
        ivAvatar = findViewById(R.id.ivAvatar);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            if (bundle.containsKey("uuid")) {
                uuid = bundle.getString("uuid");
            }
            data = Arguments.fromBundle(bundle);

            if (bundle.containsKey("name")) {
                String name = bundle.getString("name");
                tvName.setText(name);
            }
            if (bundle.containsKey("avatar")) {
                String avatar = bundle.getString("avatar");
                if (avatar != null) {
                    Picasso.get().load(avatar).transform(new CircleTransform()).into(ivAvatar);
                }
            }
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        v.vibrate(pattern, 0);
        player.start();

        AnimateImage acceptCallBtn = findViewById(R.id.ivAcceptCall);
        acceptCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    v.cancel();
                    player.stop();
                    acceptDialing();
                } catch (Exception e) {
                    WritableMap params = Arguments.createMap();
                    params.putString("message", e.getMessage());
                    sendEvent("error", params);
                    dismissDialing();
                }
            }
        });

        AnimateImage rejectCallBtn = findViewById(R.id.ivDeclineCall);
        rejectCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                v.cancel();
                player.stop();
                dismissDialing();
            }
        });

    }

    @Override
    public void onBackPressed() {
        // Dont back
    }

    private void acceptDialing() {
        WritableMap params = Arguments.createMap();
        params.putBoolean("done", true);
        params.putString("uuid", uuid);

        if (IncomingCallModule.reactContext.hasCurrentActivity() && isAppOnForeground()) {
            // App in foreground, send event for app to listen
            if(getIntent().getExtras()!= null){
                sendEvent("answerCall", Arguments.fromBundle(getIntent().getExtras()));
            }
            sendEvent("answerCall", params);
        } else {
            // App in background or killed, start app and add launch params
            String packageNames = IncomingCallModule.reactContext.getPackageName();
            Intent launchIntent = IncomingCallModule.reactContext.getPackageManager().getLaunchIntentForPackage(packageNames);
            String className = launchIntent.getComponent().getClassName();
            try {
                Class<?> activityClass = Class.forName(className);
                Intent i = new Intent(IncomingCallModule.reactContext, activityClass);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                Bundle b = new Bundle();
                b = Arguments.toBundle(data);
                b.putString("uuid", uuid);

                i.putExtras(b);
                IncomingCallModule.reactContext.startActivity(i);
            } catch(Exception e) {
                Log.e("RNIncomingCall", "Class not found", e);
                return;
            }
        }

        finish();
    }

    private void dismissDialing() {
        WritableMap params = Arguments.createMap();
        params.putBoolean("done", false);
        params.putString("uuid", uuid);

        if (IncomingCallModule.reactContext.hasCurrentActivity()) {
            // App in foreground or background, send event for app to listen
            if(getIntent().getExtras()!= null){
                sendEvent("endCall", Arguments.fromBundle(getIntent().getExtras()));
            }
            sendEvent("endCall", params);
        } else {
            // App killed, need to do something after
        }

        finish();
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected: ");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected: ");

    }

    @Override
    public void onConnectFailure() {
        Log.d(TAG, "onConnectFailure: ");

    }

    @Override
    public void onIncoming(ReadableMap params) {
        Log.d(TAG, "onIncoming: ");
    }

    private void sendEvent(String eventName, WritableMap params) {
        IncomingCallModule.reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private boolean isAppOnForeground() {
        /**
         * We need to check if app is in foreground otherwise the app will crash.
         * http://stackoverflow.com/questions/8489993/check-android-application-is-in-foreground-or-not
         **/
        ActivityManager activityManager = (ActivityManager) IncomingCallModule.reactContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = IncomingCallModule.reactContext.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}

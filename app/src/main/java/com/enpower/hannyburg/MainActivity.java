package com.enpower.hannyburg;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private GameSurfaceView gameView;
    private HudView hudView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hudLoop = new Runnable() {
        @Override
        public void run() {
            if (hudView != null) {
                hudView.invalidate();
            }
            handler.postDelayed(this, 33);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        HannyburgRenderer renderer = new HannyburgRenderer();
        gameView = new GameSurfaceView(this, renderer);
        hudView = new HudView(this, renderer);

        FrameLayout root = new FrameLayout(this);
        root.addView(gameView);
        root.addView(hudView);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.onResume();
        handler.post(hudLoop);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(hudLoop);
        gameView.onPause();
        super.onPause();
    }
}

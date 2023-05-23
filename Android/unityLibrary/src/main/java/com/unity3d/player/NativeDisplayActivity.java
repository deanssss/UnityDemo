package com.unity3d.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.unity3d.player.glshare.ShareEGLContextFactory;
import com.unity3d.player.glshare.ShareTextureRender;

public class NativeDisplayActivity extends Activity {
    private UnityPlayerActivity playerActivity;
    private GLSurfaceView glSurfaceView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.native_display_activity);

        // 当前环境中UnityPlayer静态持有UnityPlayerActivity
        playerActivity = (UnityPlayerActivity) UnityPlayer.currentActivity;
        playerActivity.mUnityPlayer.requestFocus();

        glSurfaceView = findViewById(R.id.glsv);
        glSurfaceView.setEGLContextClientVersion(3);

        int textureId = getIntent().getIntExtra(EXTRA_TEX_ID, 0);
        glSurfaceView.setEGLContextFactory(new ShareEGLContextFactory(playerActivity.unityConfig, playerActivity.unityContext));
        glSurfaceView.setRenderer(new ShareTextureRender(this, textureId));
    }

    // If the activity is in multi window mode or resizing the activity is allowed we will use
    // onStart/onStop (the visibility callbacks) to determine when to pause/resume.
    // Otherwise it will be done in onPause/onResume as Unity has done historically to preserve
    // existing behavior.
    @Override
    protected void onStop() {
        super.onStop();
        if (!MultiWindowSupport.getAllowResizableWindow(this))
            return;
        playerActivity.mUnityPlayer.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!MultiWindowSupport.getAllowResizableWindow(this))
            return;
        playerActivity.mUnityPlayer.resume();
    }

    // Pause Unity
    @Override
    protected void onPause() {
        super.onPause();
        MultiWindowSupport.saveMultiWindowMode(this);
        if (MultiWindowSupport.getAllowResizableWindow(this))
            return;
        playerActivity.mUnityPlayer.pause();
    }

    // Resume Unity
    @Override
    protected void onResume() {
        super.onResume();
        if (MultiWindowSupport.getAllowResizableWindow(this) && !MultiWindowSupport.isMultiWindowModeChangedToTrue(this))
            return;
        playerActivity.mUnityPlayer.resume();
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        playerActivity.mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        playerActivity.mUnityPlayer.windowFocusChanged(hasFocus);
    }

    private static final String EXTRA_TEX_ID = "texture-id";
    public static Intent createIntent(Context context, int textureId) {
        Intent intent = new Intent(context, NativeDisplayActivity.class);
        intent.putExtra(EXTRA_TEX_ID, textureId);
        return intent;
    }
}

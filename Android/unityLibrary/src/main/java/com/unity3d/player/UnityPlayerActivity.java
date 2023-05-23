package com.unity3d.player;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.unity3d.player.glshare.ShareEGLContextFactory;
import com.unity3d.player.glshare.ShareTextureRender;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class UnityPlayerActivity extends Activity implements IUnityPlayerLifecycleEvents {
    private static final String TAG = "UnityPlayerActivity";

    public EGLConfig unityConfig;
    public EGLContext unityContext;

    protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code
    private EGL10 egl = ((EGL10) EGLContext.getEGL());
    private GLSurfaceView glSurfaceView;
    private int counter = 0;
    private int sharedTextureId = 0;

    // Override this in your custom UnityPlayerActivity to tweak the command line arguments passed to the Unity Android Player
    // The command line arguments are passed as a string, separated by spaces
    // UnityPlayerActivity calls this from 'onCreate'
    // Supported: -force-gles20, -force-gles30, -force-gles31, -force-gles31aep, -force-gles32, -force-gles, -force-vulkan
    // See https://docs.unity3d.com/Manual/CommandLineArguments.html
    // @param cmdLine the current command line arguments, may be null
    // @return the modified command line string or null
    protected String updateUnityCommandLineArguments(String cmdLine) {
        return cmdLine;
    }

    // Setup activity layout
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.unity_player_activity);

        String cmdLine = updateUnityCommandLineArguments(getIntent().getStringExtra("unity"));
        getIntent().putExtra("unity", cmdLine);

        mUnityPlayer = new UnityPlayer(this, this);
        FrameLayout container = findViewById(R.id.container_fl);
        container.addView(mUnityPlayer, 0);
        mUnityPlayer.requestFocus();

        findViewById(R.id.action1_bt).setOnClickListener(v -> {
            UnityPlayer.UnitySendMessage("Canvas", "setText", "hello~" + (counter++));
        });
        findViewById(R.id.action2_bt).setOnClickListener(v -> {
            startActivity(new Intent(this, NativeSampleActivity.class));
        });
        findViewById(R.id.action3_bt).setOnClickListener(v -> {
            startActivity(NativeDisplayActivity.createIntent(this, sharedTextureId));
            overridePendingTransition(0, 0);
        });
    }

    public void initUnitySurfaceView(int textureId, int width, int height) {
        this.sharedTextureId = textureId;
        log("init unity surface. texture id:" + textureId);
        unityContext = egl.eglGetCurrentContext();
        if (unityContext == EGL10.EGL_NO_CONTEXT) {
            log("Unity EGL Context is empty.");
            return;
        }
        EGLDisplay unityDisplay = egl.eglGetCurrentDisplay();
        if (unityDisplay == EGL10.EGL_NO_DISPLAY) {
            log("Unity EGL Display is empty.");
            return;
        }
        int[] numEglConfigs = new int[1];
        EGLConfig[] eglConfigs = new EGLConfig[1];
        if (!egl.eglGetConfigs(unityDisplay, eglConfigs, eglConfigs.length, numEglConfigs)) {
            log("Get Unity EGL Configs failed.");
            return;
        }
        unityConfig = eglConfigs[0];

        runOnUiThread(() -> {
            glSurfaceView = new GLSurfaceView(this);
            glSurfaceView.setEGLContextClientVersion(3);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            FrameLayout container2 = findViewById(R.id.container2_fl);
            container2.addView(glSurfaceView, lp);

            // 共享EGLContext的构建
            glSurfaceView.setEGLContextFactory(new ShareEGLContextFactory(unityConfig, unityContext));
            glSurfaceView.setRenderer(new ShareTextureRender(this, textureId));
        });
    }

    // When Unity player unloaded move task to background
    @Override
    public void onUnityPlayerUnloaded() {
        moveTaskToBack(true);
    }

    // Callback before Unity player process is killed
    @Override
    public void onUnityPlayerQuitted() {
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // To support deep linking, we need to make sure that the client can get access to
        // the last sent intent. The clients access this through a JNI api that allows them
        // to get the intent set on launch. To update that after launch we have to manually
        // replace the intent with the one caught here.
        setIntent(intent);
        mUnityPlayer.newIntent(intent);
    }

    // Quit Unity
    @Override
    protected void onDestroy () {
        mUnityPlayer.destroy();
        super.onDestroy();
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
        mUnityPlayer.pause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!MultiWindowSupport.getAllowResizableWindow(this))
            return;
        mUnityPlayer.resume();
    }

    // Pause Unity
    @Override
    protected void onPause() {
        super.onPause();
        MultiWindowSupport.saveMultiWindowMode(this);
        if (MultiWindowSupport.getAllowResizableWindow(this))
            return;
        mUnityPlayer.pause();
    }

    // Resume Unity
    @Override
    protected void onResume() {
        super.onResume();
        if (MultiWindowSupport.getAllowResizableWindow(this) && !MultiWindowSupport.isMultiWindowModeChangedToTrue(this))
            return;
        mUnityPlayer.resume();
    }

    // Low Memory Unity
    @Override public void onLowMemory() {
        super.onLowMemory();
        mUnityPlayer.lowMemory();
    }

    // Trim Memory Unity
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL) {
            mUnityPlayer.lowMemory();
        }
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mUnityPlayer.windowFocusChanged(hasFocus);
    }

    // For some reason the multiple keyevent type is not supported by the ndk.
    // Force event injection by overriding dispatchKeyEvent().
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE)
            return mUnityPlayer.injectEvent(event);
        return super.dispatchKeyEvent(event);
    }

    // Pass any events not handled by (unfocused) views straight to UnityPlayer
    @Override public boolean onKeyUp(int keyCode, KeyEvent event)     { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)   { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer.injectEvent(event); }
    /*API12*/ public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer.injectEvent(event); }

    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        runOnUiThread(() -> mUnityPlayer.quit());
        super.onBackPressed();
    }

    private void log(String msg) {
        Log.e(TAG, msg);
    }
}

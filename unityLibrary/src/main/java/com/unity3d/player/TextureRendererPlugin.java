package com.unity3d.player;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;
import android.widget.FrameLayout;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;

public class TextureRendererPlugin implements SurfaceTexture.OnFrameAvailableListener {
    private static TextureRendererPlugin _instance;
    private Activity mUnityActivity;
    private int mTextureWidth;
    private int mTextureHeight;
    private static String TAG = "TextureRendererPlugIn";

    private static EGLContext unityContext = EGL14.EGL_NO_CONTEXT;
    private static EGLDisplay unityDisplay = EGL14.EGL_NO_DISPLAY;
    private static EGLSurface unityDrawSurface = EGL14.EGL_NO_SURFACE;
    private static EGLSurface unityReadSurface = EGL14.EGL_NO_SURFACE;

    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private int unityTextureID;

    private boolean mNewFrameAvailable;

    private TextureRendererPlugin(Activity unityActivity, int width, int height, int textureID) {
        mUnityActivity = unityActivity;
        mTextureWidth = width;
        mTextureHeight = height;
        unityTextureID = textureID;
        mNewFrameAvailable = false;

        initSurface();
    }

    public static TextureRendererPlugin Instance(
            Activity context, int viewPortWidth, int viewPortHeight, int textureID
    ) {
        if (_instance == null) {
            _instance = new TextureRendererPlugin(context, viewPortWidth, viewPortHeight,textureID);
        }
        return _instance;
    }

    public void updateSurfaceTexture() {
        if(mNewFrameAvailable) {
            if(!Thread.currentThread().getName().equals("UnityMain"))
                Log.e(TAG, "Not called from render thread and hence update texture will fail");
            mSurfaceTexture.updateTexImage();
            mNewFrameAvailable = false;
        }
    }

    private void initSurface() {
        unityContext = EGL14.eglGetCurrentContext();
        unityDisplay = EGL14.eglGetCurrentDisplay();
        unityDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        unityReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);

        if (unityContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "UnityEGLContext is invalid -> Most probably wrong thread");
            return;
        }

        EGL14.eglMakeCurrent(unityDisplay, unityDrawSurface, unityReadSurface, unityContext);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, unityTextureID);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        mSurfaceTexture = new SurfaceTexture(unityTextureID);
        mSurfaceTexture.setDefaultBufferSize(mTextureWidth, mTextureHeight);
        mSurface = new Surface(mSurfaceTexture);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        GLSurfaceView sfv = new GLSurfaceView(mUnityActivity);
        FrameLayout container = mUnityActivity.findViewById(R.id.container2_fl);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        container.addView(sfv, lp);
        sfv.setEGLContextClientVersion(3);
        sfv.setEGLWindowSurfaceFactory(new GLSurfaceView.EGLWindowSurfaceFactory() {
            @Override
            public javax.microedition.khronos.egl.EGLSurface createWindowSurface(
                    EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                    EGLConfig config, Object nativeWindow
            ) {
                return egl.eglCreateWindowSurface(display, config, mSurfaceTexture, null);
            }

            @Override
            public void destroySurface(
                    EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                    javax.microedition.khronos.egl.EGLSurface surface
            ) {
                egl.eglDestroySurface(display, surface);
            }
        });
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mNewFrameAvailable = true;
    }
}
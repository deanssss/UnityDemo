package com.unity3d.player.glshare;

import android.opengl.EGL14;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class ShareEGLContextFactory implements GLSurfaceView.EGLContextFactory {
    private final EGLConfig sharedConfig;
    private final EGLContext sharedContext;

    public ShareEGLContextFactory(EGLConfig sharedConfig, EGLContext sharedContext) {
        this.sharedConfig = sharedConfig;
        this.sharedContext = sharedContext;
    }

    @Override
    public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
        int[] contextAttrs = new int[] {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL10.EGL_NONE
        };
        return egl.eglCreateContext(display, sharedConfig, sharedContext, contextAttrs);
    }

    @Override
    public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
        egl.eglDestroyContext(display, context);
    }
}

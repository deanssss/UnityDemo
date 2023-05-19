package com.unity3d.player;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

public class UnityPlayerActivity extends Activity implements IUnityPlayerLifecycleEvents
{
    private static final String TAG = "UnityPlayerActivity";
    protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code
    private EGL10 egl = ((EGL10) EGLContext.getEGL());
    private GLSurfaceView glSurfaceView;

    // Override this in your custom UnityPlayerActivity to tweak the command line arguments passed to the Unity Android Player
    // The command line arguments are passed as a string, separated by spaces
    // UnityPlayerActivity calls this from 'onCreate'
    // Supported: -force-gles20, -force-gles30, -force-gles31, -force-gles31aep, -force-gles32, -force-gles, -force-vulkan
    // See https://docs.unity3d.com/Manual/CommandLineArguments.html
    // @param cmdLine the current command line arguments, may be null
    // @return the modified command line string or null
    protected String updateUnityCommandLineArguments(String cmdLine)
    {
        return cmdLine;
    }

    // Setup activity layout
    @Override protected void onCreate(Bundle savedInstanceState)
    {
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
            UnityPlayer.UnitySendMessage("Canvas", "setText", "hello~" + (i++));
        });
        findViewById(R.id.action2_bt).setOnClickListener(v -> {
            startActivity(new Intent(this, TestActivity.class));
        });
        findViewById(R.id.action3_bt).setOnClickListener(v -> {
            mUnityPlayer.setVisibility(View.INVISIBLE);
            mUnityPlayer.pause();
            FrameLayout.LayoutParams lp = ((FrameLayout.LayoutParams) container.getLayoutParams());
            lp.width = lp.width == -1 ? 700 : -1;
            container.setLayoutParams(lp);
            runOnUiThread(() ->{
                mUnityPlayer.resume();
                mUnityPlayer.setVisibility(View.VISIBLE);
            });
        });
    }

    public void initUnitySurfaceView(int textureId, int width, int height) {
        log("init unity surface. texture id:" + textureId);
        EGLContext unityContext = egl.eglGetCurrentContext();
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
        EGLConfig unityConfig = eglConfigs[0];

        runOnUiThread(() -> {
            glSurfaceView = new GLSurfaceView(this);
            glSurfaceView.setEGLContextClientVersion(3);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            FrameLayout container2 = findViewById(R.id.container2_fl);
            container2.addView(glSurfaceView, lp);

            glSurfaceView.setEGLContextFactory(new GLSurfaceView.EGLContextFactory() {
                @Override
                public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
                    int[] contextAttrs = new int[] {
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                            EGL10.EGL_NONE
                    };
                    return egl.eglCreateContext(display, unityConfig, unityContext, contextAttrs);
                }

                @Override
                public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
                    egl.eglDestroyContext(display, context);
                }
            });
            glSurfaceView.setRenderer(new UnityShareTextureRender(textureId));
//            SurfaceTexture surfaceTexture = new SurfaceTexture(textureId);
//            surfaceTexture.setDefaultBufferSize(width, height);
//            surfaceTexture.setOnFrameAvailableListener(v -> glSurfaceView.requestRender());

        });
    }

    class UnityShareTextureRender implements GLSurfaceView.Renderer {
        public int textureId;
        public boolean is2d = true;

        private int program;
        private String verticesShader
                = "attribute vec4 a_Position; \n"
                + "attribute vec2 a_TexCoordinate; \n"
                + "varying vec2 v_TexCoord; \n"
                + "void main() { \n"
                + "    v_TexCoord = a_TexCoordinate; \n"
                + "    gl_Position = a_Position; \n"
                + "}";
        private String fragmentShader
                = "precision mediump float; \n"
                + "uniform sampler2D u_Texture; \n"
                + "varying vec2 v_TexCoord; \n"
                + "void main() { \n"
                + "    gl_FragColor = texture2D(u_Texture, v_TexCoord);"
                + "}";
        private int vPosition;
        private int texCoord;
        private int uTexture;

        public UnityShareTextureRender(int textureId) {
            this.textureId = textureId;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES30.glClearColor(0.5f, 0.5f, 0f, 0.5f);
            if (is2d) loadTexture2D();
            else loadTexture();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            program = createProgram(verticesShader, fragmentShader);
            vPosition = GLES30.glGetAttribLocation(program, "a_Position");
            texCoord = GLES30.glGetAttribLocation(program, "a_TexCoordinate");
            uTexture = GLES30.glGetUniformLocation(program, "u_Texture");
            GLES30.glViewport(0, 0, width, height);
        }

        int change = 0;
        @Override
        public void onDrawFrame(GL10 gl) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

            FloatBuffer vertices = getVertices(change++);
            GLES30.glUseProgram(program);
            GLES30.glVertexAttribPointer(vPosition, 2, GLES30.GL_FLOAT, false, 0, vertices);
            GLES30.glEnableVertexAttribArray(vPosition);


            // 设置纹理
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            if (is2d) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
            } else {
                GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            }

            GLES30.glUniform4f(texCoord, 0f, 1f, 0f, 1f);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 3);

            UnityPlayer.UnitySendMessage("RenderImg", "updateFrame", "");
        }

        private void loadTexture() {
            //绑定到外部纹理上
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            //设置纹理过滤参数
            GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            //解除纹理绑定
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        private void loadTexture2D() {
            //绑定到外部纹理上
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
            //设置纹理过滤参数
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            //解除纹理绑定
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        }

        private int loadShader(int shaderType, String sourceCode) {
            int shader = GLES30.glCreateShader(shaderType);
            if (shader != 0) {
                GLES30.glShaderSource(shader, sourceCode);
                GLES30.glCompileShader(shader);
                int[] compiled = new int[1];
                GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
                if (compiled[0] == 0) {
                    log("shader compiled failed. shader type: " + shaderType);
                    log("shader compiled failed. shader: " + sourceCode);
                    GLES30.glDeleteShader(shader);
                    shader = 0;
                }
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }
            int program = GLES30.glCreateProgram();
            if (program != 0) {
                GLES30.glAttachShader(program, vertexShader);
                GLES30.glAttachShader(program, pixelShader);
                GLES30.glLinkProgram(program);
                int[] linkStatus = new int[1];
                GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] != GLES30.GL_TRUE) {
                    log("link program failed. program:" + GLES30.glGetProgramInfoLog(program));
                    program = 0;
                }
            }
            return program;
        }

        private float[] vertices = new float[] {
                0.0f, 0.5f,
                -0.5f, -0.5f,
                0.5f, -0.5f
        };
        private float[] textVertx = new float[] {
                0f, 0f,
                0f, 1f,
                1f, 1f,
                1f, 0f
        };

        private FloatBuffer getVertices(int change) {
            if (change >= 60) {
                vertices[5] = -1f;
            } else {
                vertices[5] = -0.5f;
            }
            if (change == 120) {
                this.change = 0;
            }
            ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
            vbb.order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuf = vbb.asFloatBuffer();
            vertexBuf.put(vertices);
            vertexBuf.position(0);

            return vertexBuf;
        }
    }

    private int i = 0;

    // When Unity player unloaded move task to background
    @Override public void onUnityPlayerUnloaded() {
        moveTaskToBack(true);
    }

    // Callback before Unity player process is killed
    @Override public void onUnityPlayerQuitted() {
    }

    @Override protected void onNewIntent(Intent intent)
    {
        // To support deep linking, we need to make sure that the client can get access to
        // the last sent intent. The clients access this through a JNI api that allows them
        // to get the intent set on launch. To update that after launch we have to manually
        // replace the intent with the one caught here.
        setIntent(intent);
        mUnityPlayer.newIntent(intent);
    }

    // Quit Unity
    @Override protected void onDestroy ()
    {
        mUnityPlayer.destroy();
        super.onDestroy();
    }

    // If the activity is in multi window mode or resizing the activity is allowed we will use
    // onStart/onStop (the visibility callbacks) to determine when to pause/resume.
    // Otherwise it will be done in onPause/onResume as Unity has done historically to preserve
    // existing behavior.
    @Override protected void onStop()
    {
        super.onStop();

        if (!MultiWindowSupport.getAllowResizableWindow(this))
            return;

        mUnityPlayer.pause();
    }

    @Override protected void onStart()
    {
        super.onStart();

        if (!MultiWindowSupport.getAllowResizableWindow(this))
            return;

        mUnityPlayer.resume();
    }

    // Pause Unity
    @Override protected void onPause()
    {
        super.onPause();

        MultiWindowSupport.saveMultiWindowMode(this);

        if (MultiWindowSupport.getAllowResizableWindow(this))
            return;

        mUnityPlayer.pause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();

        if (MultiWindowSupport.getAllowResizableWindow(this) && !MultiWindowSupport.isMultiWindowModeChangedToTrue(this))
            return;

        mUnityPlayer.resume();
    }

    // Low Memory Unity
    @Override public void onLowMemory()
    {
        super.onLowMemory();
        mUnityPlayer.lowMemory();
    }

    // Trim Memory Unity
    @Override public void onTrimMemory(int level)
    {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL)
        {
            mUnityPlayer.lowMemory();
        }
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        mUnityPlayer.windowFocusChanged(hasFocus);
    }

    // For some reason the multiple keyevent type is not supported by the ndk.
    // Force event injection by overriding dispatchKeyEvent().
    @Override public boolean dispatchKeyEvent(KeyEvent event)
    {
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

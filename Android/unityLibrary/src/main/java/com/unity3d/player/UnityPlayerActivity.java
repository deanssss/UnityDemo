package com.unity3d.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.EGL14;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;

import com.unity3d.player.util.RenderUtil;
import com.unity3d.player.util.ResReadUtils;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

public class UnityPlayerActivity extends Activity implements IUnityPlayerLifecycleEvents {
    private static final String TAG = "UnityPlayerActivity";
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
        });
    }

    public void initUnitySurfaceView(int textureId, int width, int height) {
        this.sharedTextureId = textureId;
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

            // 共享EGLContext的构建
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
        });
    }

    class UnityShareTextureRender implements GLSurfaceView.Renderer {
        public int textureId;

        private final float[] vertex = {
                -1f,  1f, 0f, // tl
                -1f, -1f, 0f, // bl
                1f, -1f, 0f, // br
                1f,  1f, 0f, // tr
        };
        private final short[] index = {
                0, 1, 2, 0, 2, 3
        };
        private final float[] textureVertex = {
                0f, 1f, // tl
                0f, 0f, // bl
                1f, 0f, // br
                1f, 1f, // tr
        };

        private ShortBuffer indexBuffer;
        private FloatBuffer texBuffer;
        private FloatBuffer vertexBuffer;

        private int program = 0;
        private int aPosition = 0;
        private int uTextureUnit = 0;
        private int aTexCoord = 0;

        public UnityShareTextureRender(int textureId) {
            this.textureId = textureId;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = createProgram(
                    ResReadUtils.readResource(UnityPlayerActivity.this, R.raw.demo_vertex_shader),
                    ResReadUtils.readResource(UnityPlayerActivity.this, R.raw.demo_fragment_shader));
            aPosition = GLES30.glGetAttribLocation(program, "a_Position");
            aTexCoord = GLES30.glGetAttribLocation(program, "a_TextureCoordinates");
            uTextureUnit = GLES30.glGetUniformLocation(program, "u_TextureUnit");

            vertexBuffer = RenderUtil.createFloatBuffer(vertex);
            texBuffer = RenderUtil.createFloatBuffer(textureVertex);
            indexBuffer = RenderUtil.createShortBuffer(index);

            loadTexture(UnityPlayerActivity.this, textureId, R.drawable.sample);
            GLES30.glClearColor(0f, 0f, 0f, 0f);
        }

        private void loadTexture(Context context, int textureId, @DrawableRes int imgRes) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = true;
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), imgRes, options);
            if (bitmap == null) {
                throw new RuntimeException("cannot decode image by id: $textureIds.");
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);

            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

            // 加载纹理到OpenGL， 读入bitmap定义的位图数据，并把它复制到当前绑定的纹理对象上。
            // TODO 不加载纹理，使用unity传过来的纹理
//            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
            // 为当前绑定的纹理自动生成所有需要的多级渐远纹理，生成MIP贴图
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
            // 避免误改动到这个纹理，提前解绑定
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES30.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

            // 顶点数据
            vertexBuffer.position(0);
            GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer);
            GLES30.glEnableVertexAttribArray(aPosition);
            // 纹理顶点
            texBuffer.position(0);
            GLES30.glEnableVertexAttribArray(aTexCoord);
            GLES30.glVertexAttribPointer(aTexCoord, 2, GLES30.GL_FLOAT, false, 0, texBuffer);
            // 设置纹理
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
            // 给片段着色器中的采样器变量sample2d赋值
            GLES30.glUniform1i(uTextureUnit, 0);

            // 绘制
            indexBuffer.position(0);
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, index.length, GLES30.GL_UNSIGNED_SHORT, indexBuffer);

            UnityPlayer.UnitySendMessage("RenderImg", "updateFrame", "");
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = RenderUtil.compileShader(GLES30.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                throw new RuntimeException("vertexShader compile failed.");
            }
            int pixelShader = RenderUtil.compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                throw new RuntimeException("fragmentShader compiled failed.");
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
                    GLES30.glDeleteProgram(program);
                    program = 0;
                }
                GLES30.glValidateProgram(program);
                int[] validateStatus = new int[1];
                GLES30.glGetProgramiv(program, GLES30.GL_VALIDATE_STATUS, validateStatus, 0);
                if (validateStatus[0] == 0) {
                    log("link program failed. program:" + GLES30.glGetProgramInfoLog(program));
                    GLES30.glDeleteProgram(program);
                    program = 0;
                }
                GLES30.glUseProgram(program);
            }
            return program;
        }
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
        if (level == TRIM_MEMORY_RUNNING_CRITICAL)
        {
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

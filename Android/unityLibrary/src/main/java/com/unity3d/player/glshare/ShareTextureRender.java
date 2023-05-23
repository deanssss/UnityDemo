package com.unity3d.player.glshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import androidx.annotation.DrawableRes;

import com.unity3d.player.R;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.util.RenderUtil;
import com.unity3d.player.util.ResReadUtils;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ShareTextureRender implements GLSurfaceView.Renderer {
    private final String TAG = "UnityShareTextureRender";
    private final Context context;
    public int textureId;

    private final float[] vertex = {
            -1f, 1f, 0f, // tl
            -1f, -1f, 0f, // bl
            1f, -1f, 0f, // br
            1f, 1f, 0f, // tr
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

    public ShareTextureRender(Context context, int textureId) {
        this.context = context;
        this.textureId = textureId;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        program = createProgram(
                ResReadUtils.readResource(context, R.raw.demo_vertex_shader),
                ResReadUtils.readResource(context, R.raw.demo_fragment_shader));
        aPosition = GLES30.glGetAttribLocation(program, "a_Position");
        aTexCoord = GLES30.glGetAttribLocation(program, "a_TextureCoordinates");
        uTextureUnit = GLES30.glGetUniformLocation(program, "u_TextureUnit");

        vertexBuffer = RenderUtil.createFloatBuffer(vertex);
        texBuffer = RenderUtil.createFloatBuffer(textureVertex);
        indexBuffer = RenderUtil.createShortBuffer(index);

        loadTexture(context, textureId, R.drawable.sample);
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

    private void log(String msg) {
        Log.e(TAG, msg);
    }
}

package com.unity3d.player;
import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class RenderUtil {
    public static int compileShader(int type, String shaderCode) {
        final int shaderId = GLES30.glCreateShader(type);
        if (shaderId == 0) {
            return 0;
        }

        GLES30.glShaderSource(shaderId, shaderCode);
        GLES30.glCompileShader(shaderId);
        final int[] compileStatus = new int[1];
        GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String logInfo = GLES30.glGetShaderInfoLog(shaderId);
            System.err.println(logInfo);
            //编译失败后删除
            GLES30.glDeleteShader(shaderId);
            return 0;
        }
        return shaderId;
    }

    public static int linkProgram(int vertexShaderId, int fragmentShaderId) {
        final int programId = GLES30.glCreateProgram();
        if (programId != 0) {
            //将顶点着色器加入到程序
            GLES30.glAttachShader(programId, vertexShaderId);
            //将片元着色器加入到程序中
            GLES30.glAttachShader(programId, fragmentShaderId);
            //链接着色器程序
            GLES30.glLinkProgram(programId);
            final int[] linkStatus = new int[1];
            GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                String logInfo = GLES30.glGetProgramInfoLog(programId);
                System.err.println(logInfo);
                GLES30.glDeleteProgram(programId);
                return 0;
            }
            return programId;
        } else {
            //创建失败
            return 0;
        }
    }

    public static final int BYTES_PER_FLOAT = 4;
    public static FloatBuffer createFloatBuffer(float[] data) {
        return ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(data);
    }

    public static final int BYTES_PER_SHORT = 2;
    public static ShortBuffer createShortBuffer(short[] data) {
        return ByteBuffer.allocateDirect(data.length * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(data);
    }
}
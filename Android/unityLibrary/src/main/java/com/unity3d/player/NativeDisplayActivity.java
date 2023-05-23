package com.unity3d.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import androidx.annotation.Nullable;

public class NativeDisplayActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.native_display_activity);
        GLSurfaceView glSurfaceView = findViewById(R.id.glsv);
        glSurfaceView.setEGLContextClientVersion(3);

    }

    private static final String EXTRA_TEX_ID = "texture-id";
    public static Intent createIntent(Context context, int textureId) {
        Intent intent = new Intent(context, NativeDisplayActivity.class);
        intent.putExtra(EXTRA_TEX_ID, textureId);
        return intent;
    }
}

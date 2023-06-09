package com.unity3d.player.camera;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

public class CameraSurfaceActivity extends Activity {
    private static final int PERMISSION_CODE = 100;

    private GLSurfaceView mGLSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermission();
    }

    private void requestPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE);
        } else {
            setupViews();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE && grantResults != null && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupViews();
            }
        }
    }

    private void setupViews() {
        //实例化一个GLSurfaceView
        mGLSurfaceView = new GLSurfaceView(this);
        mGLSurfaceView.setEGLContextClientVersion(3);
        mGLSurfaceView.setRenderer(new CameraSurfaceRenderer(mGLSurfaceView));
        setContentView(mGLSurfaceView);
    }
}
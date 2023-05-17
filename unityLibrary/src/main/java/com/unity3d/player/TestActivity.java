package com.unity3d.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class TestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_activity);
        findViewById(R.id.back_bt).setOnClickListener(v -> {
            startActivity(new Intent(this, UnityPlayerActivity.class));
            finish();
        });
        findViewById(R.id.reopen_bt).setOnClickListener(v -> {
            Intent intent = new Intent(this, UnityPlayerActivity.class);
            startActivity(intent);
        });
    }
}

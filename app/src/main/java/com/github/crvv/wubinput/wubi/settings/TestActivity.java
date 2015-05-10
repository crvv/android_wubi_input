package com.github.crvv.wubinput.wubi.settings;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

import com.github.crvv.wubinput.wubi.R;

public class TestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        ActionBar actionBar = getActionBar();
        if(actionBar != null)actionBar.setDisplayHomeAsUpEnabled(true);
        TextView testText = (TextView)findViewById(R.id.test_text1);
        testText.setText(Build.VERSION.CODENAME);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }
}

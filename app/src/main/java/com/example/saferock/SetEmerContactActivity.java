package com.example.saferock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


public class SetEmerContactActivity extends ActionBarActivity {

    private EditText numberEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_emer_contact);
        numberEditText = (EditText) findViewById(R.id.numberEditText);
    }

    public void done(View v) {
        String text = numberEditText.getText().toString().trim();
        if (text.length() > 0) {
            final String PREFS_NAME = "MyPrefsFile";

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

            settings.edit().putString("phone_number",text).commit();

            Intent intent = new Intent(this, MapsActivity.class);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Please enter a phone number.",Toast.LENGTH_SHORT);
        }
    }
}

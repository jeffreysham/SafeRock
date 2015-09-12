package com.example.saferock;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_set_emer_contact, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.done) {
            String text = numberEditText.getText().toString().trim();
            if (text.length() > 0) {
                final String PREFS_NAME = "MyPrefsFile";

                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

                settings.edit().putString("phone_number",text);

                Intent intent = new Intent(this, MapsActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please enter a phone number.",Toast.LENGTH_SHORT);
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

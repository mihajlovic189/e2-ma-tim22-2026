package com.example.slagalicaapp.ui.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.ui.fragments.LoginFragment;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new LoginFragment())
                    .commit();
        }
    }
}
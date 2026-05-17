package com.example.slagalicaapp.ui.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.ui.fragments.HomeFragment;
import com.example.slagalicaapp.ui.fragments.LoginFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            boolean canAutoLogin = user != null && (user.isAnonymous() || user.isEmailVerified());

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, canAutoLogin ? new HomeFragment() : new LoginFragment())
                    .commit();
        }
    }
}
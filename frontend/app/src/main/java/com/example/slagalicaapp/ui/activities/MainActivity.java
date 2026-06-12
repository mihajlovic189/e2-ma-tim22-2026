package com.example.slagalicaapp.ui.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.notifications.DemoNotificationTrigger;
import com.example.slagalicaapp.ui.fragments.HomeFragment;
import com.example.slagalicaapp.ui.fragments.LoginFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) sendDemos();
            });

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

            if (canAutoLogin) {
                requestNotifPermissionAndSendDemos();
            }
        }
    }

    private void requestNotifPermissionAndSendDemos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                sendDemos();
            } else {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            sendDemos();
        }
    }

    private void sendDemos() {
        DemoNotificationTrigger.sendDemoNotifications(this);
    }
}

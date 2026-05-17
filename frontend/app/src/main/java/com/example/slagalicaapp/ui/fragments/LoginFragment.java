package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.databinding.FragmentLoginBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.installations.FirebaseInstallations;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnLogin.setOnClickListener(v -> {
            String id = binding.etIdentity.getText().toString().trim();
            String pass = binding.etPassword.getText().toString();

            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pass)) {
                Toast.makeText(getContext(), "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.login(id, pass).observe(getViewLifecycleOwner(), user -> {
                if (user != null) {
                    saveUserToPrefs(user.getUid());
                    navigateToHome();
                } else {
                    Toast.makeText(getContext(), "Neuspešan login. Proverite podatke/verifikaciju.", Toast.LENGTH_LONG).show();
                }
            });
        });

        binding.goRegister.setOnClickListener(v -> requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_in, R.anim.slide_out, R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragment_container, new RegisterFragment())
                .addToBackStack(null)
                .commit());

        binding.goGuest.setOnClickListener(v -> {
            FirebaseInstallations.getInstance().getId().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String fid = task.getResult();
                    String suffix = fid.length() > 5 ? fid.substring(fid.length() - 5) : fid;
                    String guestName = "Gost_" + suffix;
                    String defaultRegion = "Srbija";

                    viewModel.loginGuest(guestName, defaultRegion).observe(getViewLifecycleOwner(), result -> {
                        if ("GUEST_SUCCESS".equals(result)) {
                            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                            if (currentUser != null) {
                                saveUserToPrefs(currentUser.getUid());
                                Toast.makeText(getContext(), "Ušli ste kao gost: " + guestName, Toast.LENGTH_SHORT).show();
                                navigateToHome();
                            }
                        } else {
                            Toast.makeText(getContext(), "Greška: " + result, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    Toast.makeText(getContext(), "Greška pri generisanju ID-ja.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        return binding.getRoot();
    }

    private void saveUserToPrefs(String uid) {
        requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("jwt_token", uid)
                .apply();
    }

    private void navigateToHome() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.databinding.FragmentRegisterBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;

public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding binding;
    private AuthViewModel authViewModel;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnRegister.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString();
            String user = binding.etUsername.getText().toString();
            String region = binding.spinnerRegion.getSelectedItem().toString();
            String pass = binding.etPassword.getText().toString();
            String confirmPass = binding.etConfirmPassword.getText().toString();

            if (pass.equals(confirmPass)) {
                authViewModel.register(email, user, region, pass).observe(getViewLifecycleOwner(), poruka -> {
                    Toast.makeText(getContext(), poruka, Toast.LENGTH_LONG).show();
                });
            } else {
                binding.etConfirmPassword.setError("Lozinke se ne podudaraju!");
            }
        });

        return binding.getRoot();
    }
}

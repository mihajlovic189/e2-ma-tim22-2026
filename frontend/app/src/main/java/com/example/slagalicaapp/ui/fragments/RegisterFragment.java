package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.data.models.User;
import com.example.slagalicaapp.databinding.FragmentRegisterBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;

public class RegisterFragment extends Fragment {
    private FragmentRegisterBinding binding;
    private AuthViewModel authViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnRegister.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String username = binding.etUsername.getText().toString().trim();
            String region = binding.etRegion.getText().toString().trim();
            String pass = binding.etPassword.getText().toString();
            String confirmPass = binding.etConfirmPassword.getText().toString();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(username) || TextUtils.isEmpty(pass)) {
                Toast.makeText(getContext(), "Sva polja su obavezna!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (pass.equals(confirmPass)) {
                User newUser = new User(email, username, region, pass);

                authViewModel.register(newUser).observe(getViewLifecycleOwner(), poruka -> {
                    Toast.makeText(getContext(), poruka, Toast.LENGTH_LONG).show();

                    if (!poruka.contains("Greška")) {
                        requireActivity().getSupportFragmentManager().popBackStack();
                    }
                });
            } else {
                binding.etConfirmPassword.setError("Lozinke se ne podudaraju!");
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
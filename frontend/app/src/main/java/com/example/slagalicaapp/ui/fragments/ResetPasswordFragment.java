package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.databinding.FragmentResetPasswordBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;

public class ResetPasswordFragment extends Fragment {

    private FragmentResetPasswordBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentResetPasswordBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnReset.setOnClickListener(v -> {

            String oldPass = binding.etOldPassword.getText().toString();
            String newPass = binding.etNewPassword.getText().toString();
            String confirm = binding.etConfirm.getText().toString();

            if (TextUtils.isEmpty(oldPass) || TextUtils.isEmpty(newPass)) {
                Toast.makeText(getContext(), "Popunite sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirm)) {
                binding.etConfirm.setError("Lozinke se ne poklapaju");
                return;
            }

            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("MyPrefs", android.content.Context.MODE_PRIVATE);
            String token = prefs.getString("jwt_token", null);

            if (token != null) {
                viewModel.resetPassword(oldPass, newPass).observe(getViewLifecycleOwner(), msg -> {
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(getContext(), "Niste ulogovani (token fali)", Toast.LENGTH_SHORT).show();
            }
        });

        return binding.getRoot();
    }
}
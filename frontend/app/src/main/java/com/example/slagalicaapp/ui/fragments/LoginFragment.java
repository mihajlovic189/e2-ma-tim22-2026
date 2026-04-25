package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.databinding.FragmentLoginBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnLogin.setOnClickListener(v -> {
            String id = binding.etIdentity.getText().toString();
            String pass = binding.etPassword.getText().toString();

            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pass)) {
                Toast.makeText(getContext(), "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.login(id, pass).observe(getViewLifecycleOwner(), user -> {
                if (user != null) {
                    String token = user.getToken();
                    android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("MyPrefs", android.content.Context.MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("jwt_token", token);
                    editor.apply();
                    Toast.makeText(getContext(), "Login uspješan", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Greška", Toast.LENGTH_SHORT).show();
                }
            });
        });

        binding.goRegister.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in,
                            R.anim.slide_out,
                            R.anim.fade_in,
                            R.anim.fade_out
                    )
                    .replace(R.id.fragment_container, new RegisterFragment())
                    .addToBackStack(null)
                    .commit();
        });

        binding.goReset.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.slide_in,
                            R.anim.slide_out,
                            R.anim.fade_in,
                            R.anim.fade_out
                    )
                    .replace(R.id.fragment_container, new ResetPasswordFragment())
                    .addToBackStack(null)
                    .commit();
        });

        return binding.getRoot();
    }
}
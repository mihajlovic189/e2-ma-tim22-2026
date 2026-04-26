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
            String id = binding.etIdentity.getText().toString().trim();
            String pass = binding.etPassword.getText().toString();

            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(pass)) {
                Toast.makeText(getContext(), "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.login(id, pass).observe(getViewLifecycleOwner(), user -> {
                if (user != null) {
                    Toast.makeText(getContext(), "Login uspešan!", Toast.LENGTH_SHORT).show();

                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new HomeFragment())
                            .commit();
                } else {
                    Toast.makeText(getContext(), "Neuspešan login. Proverite podatke/verifikaciju.", Toast.LENGTH_LONG).show();
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

        return binding.getRoot();
    }
}
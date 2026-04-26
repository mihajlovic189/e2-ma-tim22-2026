package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.R;
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

        Spinner spRegion = binding.spRegion;
        spRegion.setBackgroundResource(R.drawable.bg_spinner_field);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                getContext(), R.layout.simple_spinner_item,
                getResources().getStringArray(R.array.regioni_srbije)) {

            @Override
            public boolean isEnabled(int position) {
                return position != 0;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                if (position == 0) {
                    tv.setTextColor(ContextCompat.getColor(getContext(), R.color.hint));
                } else {
                    tv.setTextColor(ContextCompat.getColor(getContext(), R.color.text));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.simple_spinner_item);
        spRegion.setAdapter(adapter);
        spRegion.setOnItemSelectedListener(null);

        binding.btnRegister.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String username = binding.etUsername.getText().toString().trim();
            int selectedPosition = spRegion.getSelectedItemPosition();
            String region = spRegion.getSelectedItem().toString();
            String pass = binding.etPassword.getText().toString();
            String confirmPass = binding.etConfirmPassword.getText().toString();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(username) || TextUtils.isEmpty(pass)) {
                Toast.makeText(getContext(), "Sva polja su obavezna!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedPosition == 0) {
                Toast.makeText(getContext(), "Morate izabrati region!", Toast.LENGTH_SHORT).show();
                TextView errorText = (TextView)spRegion.getSelectedView();
                if (errorText != null) {
                    errorText.setError("Izaberite region");
                    errorText.setTextColor(android.graphics.Color.RED);
                }
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
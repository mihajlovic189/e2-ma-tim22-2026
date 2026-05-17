package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.databinding.FragmentHomeBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        authViewModel.loadProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                binding.headerStats.tvTokens.setText(String.valueOf(profile.getTokenCount()));
                binding.headerStats.tvStars.setText(String.valueOf(profile.getTotalStars()));
                binding.headerStats.tvLeague.setText(profile.getLeagueName());
            }
        });

        binding.btnProfile.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ProfileFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnKorakPoKorak.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new KorakPoKorakFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnMojBroj.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new MojBrojFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnAsocijacija.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AsocijacijeFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnSkocko.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SkockoFragment())
                        .addToBackStack(null)
                        .commit());

        View koZnaZnaButton = binding.getRoot().findViewById(R.id.btnKoZnaZna);
        if (koZnaZnaButton != null) {
            koZnaZnaButton.setOnClickListener(v ->
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new KoZnaZnaFragment())
                            .addToBackStack(null)
                            .commit());
        }

        View spojniceButton = binding.getRoot().findViewById(R.id.btnSpojnice);
        if (spojniceButton != null) {
            spojniceButton.setOnClickListener(v ->
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new SpojniceFragment())
                            .addToBackStack(null)
                            .commit());
        }

        binding.btnNotifications.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new NotificationsFragment())
                        .addToBackStack(null)
                        .commit());

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
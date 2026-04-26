package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.databinding.FragmentKorakPoKorakBinding;

public class KorakPoKorakFragment extends Fragment {
    private FragmentKorakPoKorakBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentKorakPoKorakBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }
}
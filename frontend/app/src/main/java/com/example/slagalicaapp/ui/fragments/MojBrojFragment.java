package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.slagalicaapp.databinding.FragmentMojBrojBinding;

public class MojBrojFragment extends Fragment {
    private FragmentMojBrojBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMojBrojBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }
}
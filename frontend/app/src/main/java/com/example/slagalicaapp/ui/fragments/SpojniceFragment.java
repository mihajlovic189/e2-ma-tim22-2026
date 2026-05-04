package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.slagalicaapp.R;

public class SpojniceFragment extends Fragment {

    public SpojniceFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        int layoutId = getResources().getIdentifier(
                "fragment_spojnice",
                "layout",
                requireContext().getPackageName()
        );
        return inflater.inflate(layoutId, container, false);
    }
}




package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        binding.btnProfile.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ProfileFragment())
                        .addToBackStack(null)
                        .commit());

        binding.btnGoToReset.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ResetPasswordFragment())
                        .addToBackStack(null)
                        .commit());

        // Igra: Korak po korak
        binding.btnKorakPoKorak.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new KorakPoKorakFragment())
                        .addToBackStack(null)
                        .commit());

        // Igra: Moj broj
        binding.btnMojBroj.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new MojBrojFragment())
                        .addToBackStack(null)
                        .commit());

        // Igra: Asocijacije
        binding.btnAsocijacija.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AsocijacijeFragment())
                        .addToBackStack(null)
                        .commit());

        // Igra: Skočko
        binding.btnSkocko.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SkockoFragment())
                        .addToBackStack(null)
                        .commit());

        // Igra: Ko zna zna
        View koZnaZnaButton = binding.getRoot().findViewById(R.id.btnKoZnaZna);
        if (koZnaZnaButton != null) {
            koZnaZnaButton.setOnClickListener(v ->
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new KoZnaZnaFragment())
                            .addToBackStack(null)
                            .commit());
        }

        // Igra: Spojnice
        View spojniceButton = binding.getRoot().findViewById(R.id.btnSpojnice);
        if (spojniceButton != null) {
            spojniceButton.setOnClickListener(v ->
                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new SpojniceFragment())
                            .addToBackStack(null)
                            .commit());
        }

        // Notifikacije
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
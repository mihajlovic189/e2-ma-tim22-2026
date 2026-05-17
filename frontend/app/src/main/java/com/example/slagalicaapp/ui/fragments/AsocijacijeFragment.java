package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.databinding.FragmentAsocijacijeBinding;
import com.example.slagalicaapp.ui.header.GameHeaderController;

import java.util.HashMap;
import java.util.Map;

public class AsocijacijeFragment extends Fragment {

    private static final String TAG = "AsocijacijeFragment";
    private FragmentAsocijacijeBinding binding;
    private Map<Integer, String> gameWordsMap;

    /** Kontroler za deljeni gornji GUI (Igrac 1 | Tajmer | Igrac 2). */
    private GameHeaderController headerController;

    public AsocijacijeFragment() {}

    public static AsocijacijeFragment newInstance() {
        return new AsocijacijeFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAsocijacijeBinding.inflate(inflater, container, false);
        setupGameWords();
        setupButtonClickListeners();
        setupGameHeader();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Vazno: zaustaviti CountDownTimer da ne pravi memory leak
        if (headerController != null) {
            headerController.release();
            headerController = null;
        }
        binding = null;
    }

    // ----------------- HEADER + TAJMER (1. KT) -----------------

    private void setupGameHeader() {
        headerController = new GameHeaderController(binding.getRoot());
        headerController.setPlayerNames("IGRAČ 1", "IGRAČ 2");
        headerController.setOnTimerFinishedListener(this::onTimerExpired);
        headerController.start();
        Log.d(TAG, "Asocijacije: tajmer pokrenut na 60s.");
    }

    /** Poziva se kad istekne vreme za rundu. */
    private void onTimerExpired() {
        Log.d(TAG, "Asocijacije: vreme isteklo.");
        if (getContext() != null) {
            Toast.makeText(getContext(),
                    "Vreme je isteklo!", Toast.LENGTH_SHORT).show();
        }
        // Onemoguci dalji unos kad istekne vreme.
        disableAllGameButtons();
    }

    private void disableAllGameButtons() {
        if (gameWordsMap == null || binding == null) return;
        for (Integer buttonId : gameWordsMap.keySet()) {
            Button b = getButtonById(buttonId);
            if (b != null) b.setEnabled(false);
        }
    }

    private void setupGameWords() {
        gameWordsMap = new HashMap<>();

        gameWordsMap.put(R.id.btn_a1, "SRBIJA");
        gameWordsMap.put(R.id.btn_a2, "GRČKA");
        gameWordsMap.put(R.id.btn_a3, "ITALIJA");
        gameWordsMap.put(R.id.btn_a4, "ŠPANIJA");

        gameWordsMap.put(R.id.btn_b1, "CRVENA");
        gameWordsMap.put(R.id.btn_b2, "PLAVA");
        gameWordsMap.put(R.id.btn_b3, "ZELENA");
        gameWordsMap.put(R.id.btn_b4, "ŽUTA");

        gameWordsMap.put(R.id.btn_c1, "BEOGRAD");
        gameWordsMap.put(R.id.btn_c2, "NOVI SAD");
        gameWordsMap.put(R.id.btn_c3, "NIŠ");
        gameWordsMap.put(R.id.btn_c4, "KRAGUJEVAC");

        gameWordsMap.put(R.id.btn_d1, "DUNAV");
        gameWordsMap.put(R.id.btn_d2, "SAVA");
        gameWordsMap.put(R.id.btn_d3, "TISA");
        gameWordsMap.put(R.id.btn_d4, "DRINA");
    }

    private void setupButtonClickListeners() {
        for (Integer buttonId : gameWordsMap.keySet()) {
            Button button = getButtonById(buttonId);
            if (button != null) {
                button.setOnClickListener(v -> {
                    String hiddenWord = gameWordsMap.get(v.getId());
                    if (hiddenWord != null) {
                        button.setText(hiddenWord);
                        button.setEnabled(false);
                        button.setTextColor(0xFF2196F3);
                        button.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFFFFFFFF)
                        );
                        Log.d(TAG, "Polje otvoreno: " + hiddenWord);
                    }
                });
            }
        }
    }

    private Button getButtonById(Integer id) {
        if (id == R.id.btn_a1) return binding.btnA1;
        if (id == R.id.btn_a2) return binding.btnA2;
        if (id == R.id.btn_a3) return binding.btnA3;
        if (id == R.id.btn_a4) return binding.btnA4;

        if (id == R.id.btn_b1) return binding.btnB1;
        if (id == R.id.btn_b2) return binding.btnB2;
        if (id == R.id.btn_b3) return binding.btnB3;
        if (id == R.id.btn_b4) return binding.btnB4;

        if (id == R.id.btn_c1) return binding.btnC1;
        if (id == R.id.btn_c2) return binding.btnC2;
        if (id == R.id.btn_c3) return binding.btnC3;
        if (id == R.id.btn_c4) return binding.btnC4;

        if (id == R.id.btn_d1) return binding.btnD1;
        if (id == R.id.btn_d2) return binding.btnD2;
        if (id == R.id.btn_d3) return binding.btnD3;
        if (id == R.id.btn_d4) return binding.btnD4;

        return null;
    }
}
package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.example.slagalicaapp.R;

public class SkockoFragment extends Fragment {

    // Trenutni red i kolona gdje dodajemo simbol
    private int currentRow = 1;
    private int currentCol = 1;

    // Maksimalno 6 redova, 4 kolone
    private static final int MAX_ROWS = 6;
    private static final int MAX_COLS = 4;

    public SkockoFragment() {}

    public static SkockoFragment newInstance() {
        return new SkockoFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_skocko, container, false);
        setupSymbolButtons(view);
        return view;
    }

    private void setupSymbolButtons(View view) {
        int[] symbolButtons = {
                R.id.btn_karo,
                R.id.btn_srce,
                R.id.btn_tref,
                R.id.btn_pik,
                R.id.btn_sova,
                R.id.btn_zvezda
        };

        for (int btnId : symbolButtons) {
            ImageButton btn = view.findViewById(btnId);
            btn.setOnClickListener(v -> {
                if (currentRow <= MAX_ROWS && currentCol <= MAX_COLS) {
                    // Pronađi odgovarajući ImageView u gridu
                    ImageView cell = getCellView(view, currentRow, currentCol);
                    if (cell != null) {
                        // Postavi isti src kao kliknuto dugme
                        cell.setImageDrawable(((ImageButton) v).getDrawable());
                        cell.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFFFFFFFF)
                        );

                        // Pomjeri na sljedeću kolonu
                        currentCol++;
                        if (currentCol > MAX_COLS) {
                            // Kraj reda — pređi na sljedeći red
                            currentCol = 1;
                            currentRow++;
                        }
                    }
                }
            });
        }
    }

    private ImageView getCellView(View view, int row, int col) {
        int[][] cellIds = {
                { R.id.r1c1, R.id.r1c2, R.id.r1c3, R.id.r1c4 },
                { R.id.r2c1, R.id.r2c2, R.id.r2c3, R.id.r2c4 },
                { R.id.r3c1, R.id.r3c2, R.id.r3c3, R.id.r3c4 },
                { R.id.r4c1, R.id.r4c2, R.id.r4c3, R.id.r4c4 },
                { R.id.r5c1, R.id.r5c2, R.id.r5c3, R.id.r5c4 },
                { R.id.r6c1, R.id.r6c2, R.id.r6c3, R.id.r6c4 }
        };

        if (row < 1 || row > MAX_ROWS || col < 1 || col > MAX_COLS) return null;
        return view.findViewById(cellIds[row - 1][col - 1]);
    }
}
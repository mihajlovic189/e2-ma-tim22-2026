package com.example.slagalicaapp.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.slagalicaapp.R;

public class SkockoFragment extends Fragment {

    private int currentRow = 1;
    private int currentCol = 1;

    private static final int MAX_ROWS = 6;
    private static final int MAX_COLS = 4;

    private Button btnConfirmRow;
    private View rootView;

    public SkockoFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_skocko, container, false);

        btnConfirmRow = rootView.findViewById(R.id.btn_confirm_row);

        setupButtons(rootView);
        setupConfirmButton();
        setupCellClickRemoving(rootView);

        return rootView;
    }

    private void setupButtons(View view) {
        int[] buttons = {
                R.id.btn_karo,
                R.id.btn_srce,
                R.id.btn_tref,
                R.id.btn_pik,
                R.id.btn_sova,
                R.id.btn_zvezda
        };

        for (int id : buttons) {
            ImageButton btn = view.findViewById(id);

            btn.setOnClickListener(v -> {
                if (currentRow > MAX_ROWS) return;
                if (currentCol > MAX_COLS) return;

                ImageView cell = getCellView(view, currentRow, currentCol);

                if (cell != null) {
                    cell.setImageDrawable(btn.getDrawable());
                    cell.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));

                    currentCol++;

                    if (currentCol > MAX_COLS) {
                        btnConfirmRow.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    }

    private void setupCellClickRemoving(View view) {
        for (int row = 1; row <= MAX_ROWS; row++) {
            for (int col = 1; col <= MAX_COLS; col++) {
                final int clickedRow = row;
                final int clickedCol = col;

                ImageView cell = getCellView(view, clickedRow, clickedCol);

                if (cell != null) {
                    cell.setOnClickListener(v -> {
                        if (clickedRow != currentRow) return;
                        if (clickedCol >= currentCol) return;

                        ImageView clickedCell = (ImageView) v;

                        clickedCell.setImageDrawable(null);
                        clickedCell.setBackgroundTintList(
                                ColorStateList.valueOf(0xFFE3F2FD)
                        );

                        currentCol = clickedCol;
                        btnConfirmRow.setVisibility(View.GONE);

                        clearCellsAfter(clickedCol);
                    });
                }
            }
        }
    }

    private void clearCellsAfter(int clickedCol) {
        for (int col = clickedCol + 1; col <= MAX_COLS; col++) {
            ImageView cell = getCellView(rootView, currentRow, col);

            if (cell != null) {
                cell.setImageDrawable(null);
                cell.setBackgroundTintList(
                        ColorStateList.valueOf(0xFFE3F2FD)
                );
            }
        }
    }

    private void setupConfirmButton() {
        btnConfirmRow.setOnClickListener(v -> {
            showFeedbackForCurrentRow();

            btnConfirmRow.setVisibility(View.GONE);

            currentRow++;
            currentCol = 1;
        });
    }

    private void showFeedbackForCurrentRow() {
        int[] feedbackIds = {
                R.id.fb1, R.id.fb2, R.id.fb3,
                R.id.fb4, R.id.fb5, R.id.fb6
        };

        if (currentRow >= 1 && currentRow <= MAX_ROWS) {
            View feedback = rootView.findViewById(feedbackIds[currentRow - 1]);

            if (feedback != null) {
                feedback.setVisibility(View.VISIBLE);
            }
        }
    }

    private ImageView getCellView(View view, int row, int col) {
        int[][] cellIds = {
                {R.id.r1c1, R.id.r1c2, R.id.r1c3, R.id.r1c4},
                {R.id.r2c1, R.id.r2c2, R.id.r2c3, R.id.r2c4},
                {R.id.r3c1, R.id.r3c2, R.id.r3c3, R.id.r3c4},
                {R.id.r4c1, R.id.r4c2, R.id.r4c3, R.id.r4c4},
                {R.id.r5c1, R.id.r5c2, R.id.r5c3, R.id.r5c4},
                {R.id.r6c1, R.id.r6c2, R.id.r6c3, R.id.r6c4}
        };

        if (row < 1 || row > MAX_ROWS || col < 1 || col > MAX_COLS) return null;

        return view.findViewById(cellIds[row - 1][col - 1]);
    }
}
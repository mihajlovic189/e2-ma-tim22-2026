package com.example.slagalicaapp.ui.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.data.models.ProfileData;
import com.example.slagalicaapp.databinding.FragmentProfileBinding;
import com.example.slagalicaapp.viewmodels.AuthViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.Map;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthViewModel viewModel;
    private ActivityResultLauncher<String> avatarPickerLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        avatarPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) {
                return;
            }

            if (binding != null) {
                binding.ivAvatar.setImageURI(uri);
            }

            viewModel.updateAvatarUri(uri.toString()).observe(this, msg -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.btnChangeAvatar.setOnClickListener(v -> avatarPickerLauncher.launch("image/*"));
        binding.btnLogout.setOnClickListener(v -> performLogout());

        binding.btnGoToReset.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ResetPasswordFragment())
                        .addToBackStack(null)
                        .commit());

        loadProfile();

        return binding.getRoot();
    }

    private void loadProfile() {
        viewModel.loadProfile().observe(getViewLifecycleOwner(), profileData -> {
            if (profileData == null) {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    performLogout();
                    return;
                }

                Toast.makeText(getContext(), "Profil trenutno nije dostupan, prikazujem podrazumevane podatke.", Toast.LENGTH_SHORT).show();
                profileData = createFallbackProfile();
            }

            renderProfile(profileData);
        });
    }

    private void renderProfile(ProfileData profileData) {
        binding.tvUsername.setText(profileData.getUsername());
        binding.tvEmail.setText(profileData.getEmail());
        binding.tvRegion.setText(getString(R.string.profile_region_value, profileData.getRegion()));
        binding.tvLeagueName.setText(profileData.getLeagueName());
        binding.tvTokenCount.setText(String.valueOf(profileData.getTokenCount()));
        binding.tvStarCount.setText(String.valueOf(profileData.getTotalStars()));
        binding.tvQrPayload.setText(profileData.getQrPayload());

        setAvatar(profileData.getAvatarUri());
        setLeagueIcon(profileData.getLeagueName());
        applyAvatarFrame(profileData.getPreviousCycleRank());
        renderStatGroup(binding.llAverageStats, profileData.getAverageScoreRanges());
        renderStatGroup(binding.llDetailedStats, profileData.getDetailedStats());
        renderQrCode(profileData.getQrPayload());

        if (profileData.getLeagueChangedTo() != null) {
            showLeagueChangeDialog(profileData.getLeagueChangedFrom(),
                    profileData.getLeagueChangedTo());
        }
    }

    private void applyAvatarFrame(int previousCycleRank) {
        int strokeColor;
        int strokeWidthDp;
        if (previousCycleRank == 1) {
            strokeColor = Color.parseColor("#FFD700");
            strokeWidthDp = 4;
        } else if (previousCycleRank == 2) {
            strokeColor = Color.parseColor("#C0C0C0");
            strokeWidthDp = 4;
        } else if (previousCycleRank == 3) {
            strokeColor = Color.parseColor("#CD7F32");
            strokeWidthDp = 4;
        } else {
            strokeColor = ContextCompat.getColor(requireContext(), R.color.primary);
            strokeWidthDp = 2;
        }
        binding.cardAvatar.setStrokeColor(strokeColor);
        float density = getResources().getDisplayMetrics().density;
        binding.cardAvatar.setStrokeWidth((int) (strokeWidthDp * density));
    }

    private void setAvatar(String avatarUri) {
        if (avatarUri == null || avatarUri.trim().isEmpty()) {
            binding.ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }

        try {
            binding.ivAvatar.setImageURI(Uri.parse(avatarUri));
        } catch (Exception e) {
            binding.ivAvatar.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void setLeagueIcon(String leagueName) {
        String n = leagueName == null ? "" : leagueName.toLowerCase();
        int iconRes;
        if (n.contains("master")) {
            iconRes = R.drawable.ic_trophy;
        } else if (n.contains("diamond")) {
            iconRes = R.drawable.ic_diamond;
        } else if (n.contains("gold")) {
            iconRes = android.R.drawable.btn_star_big_on;
        } else if (n.contains("silver")) {
            iconRes = android.R.drawable.star_big_off;   // swapped: silver gets bronze drawable
        } else if (n.contains("bronze")) {
            iconRes = android.R.drawable.star_big_on;    // swapped: bronze gets silver drawable
        } else {
            iconRes = android.R.drawable.ic_menu_compass; // Rookie
        }
        binding.ivLeagueIcon.setImageResource(iconRes);
    }

    private void showLeagueChangeDialog(String from, String to) {
        if (!isAdded() || getContext() == null) return;

        boolean promoted = isPromotion(from, to);
        String title   = promoted ? "🎉 Napredovanje u ligi!" : "⬇ Pad u ligi";
        String message = promoted
                ? "Čestitamo! Napredovao si iz lige\n\"" + from + "\"\nu ligu\n\"" + to + "\"!"
                : "Pao si iz lige\n\"" + from + "\"\nu ligu\n\"" + to + "\".";

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("U redu", null)
                .show();
    }

    private boolean isPromotion(String from, String to) {
        int fromIdx = leagueIndex(from);
        int toIdx   = leagueIndex(to);
        return toIdx > fromIdx;
    }

    private int leagueIndex(String name) {
        if (name == null) return 0;
        for (com.example.slagalicaapp.data.models.League l :
                com.example.slagalicaapp.utils.LeagueManager.LEAGUES) {
            if (l.name.equals(name)) return l.index;
        }
        return 0;
    }

    private void renderStatGroup(LinearLayout container, Map<String, String> stats) {
        container.removeAllViews();

        if (stats == null || stats.isEmpty()) {
            container.addView(createStatRow(getString(R.string.profile_no_stats), "—"));
            return;
        }

        for (Map.Entry<String, String> entry : stats.entrySet()) {
            container.addView(createStatRow(entry.getKey(), entry.getValue()));
        }
    }

    private View createStatRow(String title, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, 18);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14f);
        tvTitle.setText(title);

        TextView tvValue = new TextView(requireContext());
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvValue.setTextColor(getResources().getColor(R.color.hint, requireContext().getTheme()));
        tvValue.setTextSize(13f);
        tvValue.setText(value);

        row.addView(tvTitle);
        row.addView(tvValue);

        return row;
    }

    private void renderQrCode(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            binding.ivQrCode.setImageResource(android.R.drawable.ic_menu_share);
            return;
        }

        try {
            BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 600, 600);
            Bitmap bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < 600; x++) {
                for (int y = 0; y < 600; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            binding.ivQrCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            binding.ivQrCode.setImageResource(android.R.drawable.ic_menu_share);
        }
    }

    private ProfileData createFallbackProfile() {
        ProfileData profileData = new ProfileData();
        profileData.setUid("local");
        profileData.setUsername("Igrač");
        profileData.setEmail("Nije dostupno");
        profileData.setRegion("Nepoznat region");
        profileData.setLeagueName("Rookie");
        profileData.setQrPayload("slagalica://invite?uid=local&username=Igrač");
        profileData.setTokenCount(0);
        profileData.setTotalStars(0);
        profileData.setAverageScoreRanges(null);
        profileData.setDetailedStats(null);
        return profileData;
    }

    private void performLogout() {
        viewModel.logout();
        requireActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("jwt_token")
                .apply();

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new LoginFragment())
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}


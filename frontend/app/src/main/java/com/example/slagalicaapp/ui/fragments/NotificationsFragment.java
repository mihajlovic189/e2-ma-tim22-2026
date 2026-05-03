package com.example.slagalicaapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.slagalicaapp.R;
import com.example.slagalicaapp.ui.adapters.NotificationAdapter;
import java.util.Arrays;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private Button btnTab1, btnTab2, btnTab3, btnTab4;
    private Button btnFilter;
    private RecyclerView rvLista1, rvLista2, rvLista3, rvLista4;
    private int trenutniFilter = NotificationAdapter.FILTER_SVE;

    public NotificationsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        btnTab1 = view.findViewById(R.id.btnTab1);
        btnTab2 = view.findViewById(R.id.btnTab2);
        btnTab3 = view.findViewById(R.id.btnTab3);
        btnTab4 = view.findViewById(R.id.btnTab4);
        btnFilter = view.findViewById(R.id.btnFilter);
        rvLista1 = view.findViewById(R.id.rvLista1);
        rvLista2 = view.findViewById(R.id.rvLista2);
        rvLista3 = view.findViewById(R.id.rvLista3);
        rvLista4 = view.findViewById(R.id.rvLista4);

        setupListe();
        setActiveTab(1);

        btnTab1.setOnClickListener(v -> setActiveTab(1));
        btnTab2.setOnClickListener(v -> setActiveTab(2));
        btnTab3.setOnClickListener(v -> setActiveTab(3));
        btnTab4.setOnClickListener(v -> setActiveTab(4));

        btnFilter.setOnClickListener(v -> {
            trenutniFilter = (trenutniFilter + 1) % 3;

            switch (trenutniFilter) {
                case NotificationAdapter.FILTER_SVE:
                    btnFilter.setText("Sve");
                    btnFilter.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF607D8B));
                    break;
                case NotificationAdapter.FILTER_NEPROCITANE:
                    btnFilter.setText("Nepročitane");
                    btnFilter.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF2196F3));
                    break;
                case NotificationAdapter.FILTER_PROCITANE:
                    btnFilter.setText("Pročitane");
                    btnFilter.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF4CAF50));
                    break;
            }

            getActiveAdapter().setFilter(trenutniFilter);
        });

        return view;
    }

    private void setupListe() {
        List<NotificationAdapter.NotificationItem> lista1 = Arrays.asList(
                new NotificationAdapter.NotificationItem("Marko ti je poslao poruku", "Hej, igramo večeras?", "5 min"),
                new NotificationAdapter.NotificationItem("Ana te izazvala", "Ana želi da igraš protiv nje.", "30 min"),
                new NotificationAdapter.NotificationItem("Novi čet", "Pridružio si se novoj grupi.", "3 h")
        );

        List<NotificationAdapter.NotificationItem> lista2 = Arrays.asList(
                new NotificationAdapter.NotificationItem("Nova rang lista", "Tvoj rang je porastao na #5!", "10 min"),
                new NotificationAdapter.NotificationItem("Pao si na rang listi", "Neko te pretekao. Igraj više!", "2 h"),
                new NotificationAdapter.NotificationItem("Top 10!", "Ušao si u top 10 igrača.", "1 dan")
        );

        List<NotificationAdapter.NotificationItem> lista3 = Arrays.asList(
                new NotificationAdapter.NotificationItem("Nova nagrada dostupna", "Osvoji nagradu za 5 pobeda.", "1 h"),
                new NotificationAdapter.NotificationItem("Nagrada ističe", "Tvoja nagrada ističe za 24h!", "3 h"),
                new NotificationAdapter.NotificationItem("Čestitamo!", "Dobio si zlatnu medalju.", "2 dana")
        );

        List<NotificationAdapter.NotificationItem> lista4 = Arrays.asList(
                new NotificationAdapter.NotificationItem("Ažuriranje aplikacije", "Nova verzija 2.1 je dostupna.", "1 dan"),
                new NotificationAdapter.NotificationItem("Sigurnost naloga", "Prijavljivanje sa novog uređaja.", "2 dana"),
                new NotificationAdapter.NotificationItem("Servis u toku", "Planirano održavanje u 02:00.", "3 dana")
        );

        rvLista1.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLista2.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLista3.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLista4.setLayoutManager(new LinearLayoutManager(getContext()));

        rvLista1.setAdapter(new NotificationAdapter(lista1));
        rvLista2.setAdapter(new NotificationAdapter(lista2));
        rvLista3.setAdapter(new NotificationAdapter(lista3));
        rvLista4.setAdapter(new NotificationAdapter(lista4));
    }

    private void setActiveTab(int tab) {
        rvLista1.setVisibility(View.GONE);
        rvLista2.setVisibility(View.GONE);
        rvLista3.setVisibility(View.GONE);
        rvLista4.setVisibility(View.GONE);

        int inactive = 0xFFBBDEFB;
        int active   = 0xFF2196F3;
        btnTab1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTab2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTab3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTab4.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTab1.setTextColor(0xFF000000);
        btnTab2.setTextColor(0xFF000000);
        btnTab3.setTextColor(0xFF000000);
        btnTab4.setTextColor(0xFF000000);

        switch (tab) {
            case 1:
                rvLista1.setVisibility(View.VISIBLE);
                btnTab1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active));
                btnTab1.setTextColor(0xFFFFFFFF);
                break;
            case 2:
                rvLista2.setVisibility(View.VISIBLE);
                btnTab2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active));
                btnTab2.setTextColor(0xFFFFFFFF);
                break;
            case 3:
                rvLista3.setVisibility(View.VISIBLE);
                btnTab3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active));
                btnTab3.setTextColor(0xFFFFFFFF);
                break;
            case 4:
                rvLista4.setVisibility(View.VISIBLE);
                btnTab4.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active));
                btnTab4.setTextColor(0xFFFFFFFF);
                break;
        }

        // Reset filter pri promjeni taba
        trenutniFilter = NotificationAdapter.FILTER_SVE;
        btnFilter.setText("Sve");
        btnFilter.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF607D8B));
    }

    private NotificationAdapter getActiveAdapter() {
        if (rvLista1.getVisibility() == View.VISIBLE)
            return (NotificationAdapter) rvLista1.getAdapter();
        if (rvLista2.getVisibility() == View.VISIBLE)
            return (NotificationAdapter) rvLista2.getAdapter();
        if (rvLista3.getVisibility() == View.VISIBLE)
            return (NotificationAdapter) rvLista3.getAdapter();
        return (NotificationAdapter) rvLista4.getAdapter();
    }
}
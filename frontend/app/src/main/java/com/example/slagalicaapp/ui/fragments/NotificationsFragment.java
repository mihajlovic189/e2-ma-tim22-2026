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
import com.example.slagalicaapp.repositories.NotificationRepository;
import com.example.slagalicaapp.ui.adapters.NotificationAdapter;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private Button btnTab1, btnTab2, btnTab3, btnTab4;
    private Button btnFilter;
    private RecyclerView rvLista1, rvLista2, rvLista3, rvLista4;
    private int trenutniFilter = NotificationAdapter.FILTER_SVE;

    private NotificationRepository repo;
    private NotificationAdapter adapterChat, adapterRanking, adapterRewards, adapterOther;

    public NotificationsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        btnTab1   = view.findViewById(R.id.btnTab1);
        btnTab2   = view.findViewById(R.id.btnTab2);
        btnTab3   = view.findViewById(R.id.btnTab3);
        btnTab4   = view.findViewById(R.id.btnTab4);
        btnFilter = view.findViewById(R.id.btnFilter);
        rvLista1  = view.findViewById(R.id.rvLista1);
        rvLista2  = view.findViewById(R.id.rvLista2);
        rvLista3  = view.findViewById(R.id.rvLista3);
        rvLista4  = view.findViewById(R.id.rvLista4);

        repo = new NotificationRepository();

        adapterChat    = new NotificationAdapter(repo);
        adapterRanking = new NotificationAdapter(repo);
        adapterRewards = new NotificationAdapter(repo);
        adapterOther   = new NotificationAdapter(repo);

        rvLista1.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLista2.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLista3.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLista4.setLayoutManager(new LinearLayoutManager(getContext()));

        rvLista1.setAdapter(adapterChat);
        rvLista2.setAdapter(adapterRanking);
        rvLista3.setAdapter(adapterRewards);
        rvLista4.setAdapter(adapterOther);

        observeNotifications();
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

    private void observeNotifications() {
        repo.getNotifikacije().observe(getViewLifecycleOwner(), items -> {
            List<NotificationAdapter.NotificationItem> chat    = new ArrayList<>();
            List<NotificationAdapter.NotificationItem> ranking = new ArrayList<>();
            List<NotificationAdapter.NotificationItem> rewards = new ArrayList<>();
            List<NotificationAdapter.NotificationItem> other   = new ArrayList<>();

            for (NotificationRepository.NotifItem n : items) {
                NotificationAdapter.NotificationItem ui =
                        new NotificationAdapter.NotificationItem(
                                n.id, n.naslov, n.opis, formatTime(n.timestamp), n.procitana);
                switch (n.tip != null ? n.tip : "other") {
                    case "chat":    chat.add(ui);    break;
                    case "ranking": ranking.add(ui); break;
                    case "rewards": rewards.add(ui); break;
                    default:        other.add(ui);   break;
                }
            }

            adapterChat.updateItems(chat);
            adapterRanking.updateItems(ranking);
            adapterRewards.updateItems(rewards);
            adapterOther.updateItems(other);
        });
    }

    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / 60_000;
        if (minutes < 1)   return "upravo";
        if (minutes < 60)  return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24)    return hours + " h";
        return (hours / 24) + " dana";
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

        trenutniFilter = NotificationAdapter.FILTER_SVE;
        btnFilter.setText("Sve");
        btnFilter.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF607D8B));
    }

    private NotificationAdapter getActiveAdapter() {
        if (rvLista1.getVisibility() == View.VISIBLE) return adapterChat;
        if (rvLista2.getVisibility() == View.VISIBLE) return adapterRanking;
        if (rvLista3.getVisibility() == View.VISIBLE) return adapterRewards;
        return adapterOther;
    }
}

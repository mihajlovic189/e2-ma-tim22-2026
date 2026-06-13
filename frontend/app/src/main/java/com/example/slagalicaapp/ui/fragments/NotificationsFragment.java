package com.example.slagalicaapp.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicaapp.R;
import com.example.slagalicaapp.model.NotificationModel;
import com.example.slagalicaapp.notifications.DemoNotificationTrigger;
import com.example.slagalicaapp.repositories.NotificationStore;
import com.example.slagalicaapp.ui.adapters.NotificationAdapter;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private TextView emptyView;

    private final List<NotificationModel> allNotifications = new ArrayList<>();
    private String activeTab    = "sve";  // sve | chat | ranking | rewards | other
    private String activeFilter = "sve";  // sve | neprocitane | procitane

    private Button btnTabSve, btnTabChat, btnTabRanking, btnTabRewards, btnTabOther;
    private Button btnFilterAll, btnFilterUnread, btnFilterRead;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_notifications);
        emptyView    = view.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new NotificationAdapter(new ArrayList<>(), new NotificationAdapter.NotificationListener() {
            @Override
            public void onMarkRead(NotificationModel n) {
                NotificationStore.markAsRead(requireContext(), n.getId());
                n.setRead(true);
                applyFilter();
            }

            @Override
            public void onAction(NotificationModel n) {
                NotificationStore.markAsRead(requireContext(), n.getId());
                n.setRead(true);
                applyFilter();
                handleAction(n);
            }
        });
        recyclerView.setAdapter(adapter);

        bindTabButtons(view);
        bindFilterButtons(view);

        view.findViewById(R.id.btnAddTest).setOnClickListener(v -> {
            DemoNotificationTrigger.addTestNotifications(requireContext());
            loadNotifications();
            Toast.makeText(getContext(), "Dodane 4 test notifikacije", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        if (getContext() == null) return;
        allNotifications.clear();
        allNotifications.addAll(NotificationStore.getAll(requireContext()));
        applyFilter();
    }

    // ---- Tab buttons ----

    private void bindTabButtons(View root) {
        btnTabSve     = root.findViewById(R.id.btnTabSve);
        btnTabChat    = root.findViewById(R.id.btnTabChat);
        btnTabRanking = root.findViewById(R.id.btnTabRanking);
        btnTabRewards = root.findViewById(R.id.btnTabRewards);
        btnTabOther   = root.findViewById(R.id.btnTabOther);

        btnTabSve.setOnClickListener(v     -> selectTab("sve"));
        btnTabChat.setOnClickListener(v    -> selectTab("chat"));
        btnTabRanking.setOnClickListener(v -> selectTab("ranking"));
        btnTabRewards.setOnClickListener(v -> selectTab("rewards"));
        btnTabOther.setOnClickListener(v   -> selectTab("other"));

        updateTabVisuals();
    }

    private void selectTab(String tab) {
        activeTab = tab;
        updateTabVisuals();
        applyFilter();
    }

    private void updateTabVisuals() {
        setTabStyle(btnTabSve,     "sve");
        setTabStyle(btnTabChat,    "chat");
        setTabStyle(btnTabRanking, "ranking");
        setTabStyle(btnTabRewards, "rewards");
        setTabStyle(btnTabOther,   "other");
    }

    private void setTabStyle(Button btn, String tab) {
        if (btn == null) return;
        if (tab.equals(activeTab)) {
            btn.setBackgroundColor(Color.parseColor("#1565C0"));
            btn.setTextColor(Color.WHITE);
        } else {
            btn.setBackgroundColor(Color.parseColor("#E3F2FD"));
            btn.setTextColor(Color.parseColor("#1565C0"));
        }
    }

    // ---- Filter buttons ----

    private void bindFilterButtons(View root) {
        btnFilterAll    = root.findViewById(R.id.btnFilterAll);
        btnFilterUnread = root.findViewById(R.id.btnFilterUnread);
        btnFilterRead   = root.findViewById(R.id.btnFilterRead);

        btnFilterAll.setOnClickListener(v    -> selectFilter("sve"));
        btnFilterUnread.setOnClickListener(v -> selectFilter("neprocitane"));
        btnFilterRead.setOnClickListener(v   -> selectFilter("procitane"));

        updateFilterVisuals();
    }

    private void selectFilter(String filter) {
        activeFilter = filter;
        updateFilterVisuals();
        applyFilter();
    }

    private void updateFilterVisuals() {
        setFilterStyle(btnFilterAll,    "sve");
        setFilterStyle(btnFilterUnread, "neprocitane");
        setFilterStyle(btnFilterRead,   "procitane");
    }

    private void setFilterStyle(Button btn, String filter) {
        if (btn == null) return;
        if (filter.equals(activeFilter)) {
            btn.setBackgroundColor(Color.parseColor("#1A237E"));
            btn.setTextColor(Color.WHITE);
        } else {
            btn.setBackgroundColor(Color.parseColor("#C5CAE9"));
            btn.setTextColor(Color.parseColor("#1A237E"));
        }
    }

    // ---- Filter logic ----

    private void applyFilter() {
        List<NotificationModel> filtered = new ArrayList<>();
        for (NotificationModel n : allNotifications) {
            boolean matchesTab = activeTab.equals("sve") || activeTab.equals(n.getType());
            boolean matchesFilter;
            switch (activeFilter) {
                case "procitane":   matchesFilter = n.isRead();  break;
                case "neprocitane": matchesFilter = !n.isRead(); break;
                default:            matchesFilter = true;        break;
            }
            if (matchesTab && matchesFilter) filtered.add(n);
        }

        adapter.updateData(filtered);
        boolean empty = filtered.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE  : View.VISIBLE);
        emptyView.setVisibility(empty   ? View.VISIBLE : View.GONE);
    }

    // ---- Actions ----

    private void handleAction(NotificationModel n) {
        switch (n.getType()) {
            case "chat":
                Toast.makeText(getContext(), "Otvaranje četa...", Toast.LENGTH_SHORT).show();
                break;
            case "ranking":
                Toast.makeText(getContext(), "Otvaranje rang liste...", Toast.LENGTH_SHORT).show();
                break;
            case "rewards":
                Toast.makeText(getContext(), "Otvaranje nagrada...", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(getContext(), "Akcija izvršena", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}

// NotificationAdapter.java
package com.example.slagalicaapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.slagalicaapp.R;
import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public static class NotificationItem {
        public String naslov;
        public String opis;
        public String vrijeme;
        public boolean procitana = false;

        public NotificationItem(String naslov, String opis, String vrijeme) {
            this.naslov = naslov;
            this.opis = opis;
            this.vrijeme = vrijeme;
        }
    }

    // SVE notifikacije
    private List<NotificationItem> sveStavke;
    // Trenutno prikazane (filtrirane)
    private List<NotificationItem> prikazaneStavke;

    public static final int FILTER_SVE = 0;
    public static final int FILTER_NEPROCITANE = 1;
    public static final int FILTER_PROCITANE = 2;

    private int trenutniFilter = FILTER_SVE;

    public NotificationAdapter(List<NotificationItem> items) {
        this.sveStavke = items;
        this.prikazaneStavke = new ArrayList<>(items);
    }

    public void setFilter(int filter) {
        trenutniFilter = filter;
        prikazaneStavke = new ArrayList<>();
        for (NotificationItem item : sveStavke) {
            if (filter == FILTER_SVE) {
                prikazaneStavke.add(item);
            } else if (filter == FILTER_PROCITANE && item.procitana) {
                prikazaneStavke.add(item);
            } else if (filter == FILTER_NEPROCITANE && !item.procitana) {
                prikazaneStavke.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = prikazaneStavke.get(position);
        holder.tvNaslov.setText(item.naslov);
        holder.tvOpis.setText(item.opis);
        holder.tvVrijeme.setText(item.vrijeme);

        if (item.procitana) {
            holder.itemView.setBackgroundColor(0xFFCFD8DC);
            holder.tvNaslov.setTextColor(0xFF607D8B);
            holder.tvOpis.setTextColor(0xFF90A4AE);
            holder.tvVrijeme.setTextColor(0xFF90A4AE);
        } else {
            holder.itemView.setBackgroundColor(0xFFFFFFFF);
            holder.tvNaslov.setTextColor(0xFF000000);
            holder.tvOpis.setTextColor(0xFF333333);
            holder.tvVrijeme.setTextColor(0xFF555555);
        }

        holder.itemView.setOnClickListener(v -> {
            item.procitana = true;
            // Osvježi filter nakon klika
            setFilter(trenutniFilter);
        });
    }

    @Override
    public int getItemCount() {
        return prikazaneStavke.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNaslov, tvOpis, tvVrijeme;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNaslov = itemView.findViewById(R.id.tvNaslov);
            tvOpis = itemView.findViewById(R.id.tvOpis);
            tvVrijeme = itemView.findViewById(R.id.tvVrijeme);
        }
    }
}
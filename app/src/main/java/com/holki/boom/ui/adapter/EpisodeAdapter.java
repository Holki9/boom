package com.holki.boom.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.holki.boom.R;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {
    private final Context context;
    private final int episodeCount;
    private int selectedEpisode = 1;
    private final OnEpisodeClickListener listener;

    public interface OnEpisodeClickListener {
        void onEpisodeClick(int episodeNumber);
    }

    public EpisodeAdapter(Context context, int episodeCount, OnEpisodeClickListener listener) {
        this.context = context;
        this.episodeCount = episodeCount;
        this.listener = listener;
    }

    public void setSelectedEpisode(int episode) {
        this.selectedEpisode = episode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_episode_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int ep = position + 1;
        holder.tvEpisode.setText("Серия " + ep);
        boolean isSelected = (ep == selectedEpisode);
        holder.tvEpisode.setBackgroundResource(isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        holder.tvEpisode.setTextColor(context.getColor(isSelected ? R.color.white : R.color.text_primary));

        holder.itemView.setOnClickListener(v -> {
            selectedEpisode = ep;
            notifyDataSetChanged();
            if (listener != null) listener.onEpisodeClick(ep);
        });
    }

    @Override
    public int getItemCount() {
        return Math.max(1, episodeCount);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEpisode;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpisode = itemView.findViewById(R.id.tvEpisodeChip);
        }
    }
}

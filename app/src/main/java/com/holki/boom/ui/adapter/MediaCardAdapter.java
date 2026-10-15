package com.holki.boom.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.holki.boom.R;
import com.holki.boom.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

public class MediaCardAdapter extends RecyclerView.Adapter<MediaCardAdapter.ViewHolder> {
    private final Context context;
    private List<MediaItem> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final boolean isGrid;

    public interface OnItemClickListener {
        void onItemClick(MediaItem item);
    }

    public MediaCardAdapter(Context context, OnItemClickListener listener, boolean isGrid) {
        this.context = context;
        this.listener = listener;
        this.isGrid = isGrid;
    }

    public void setItems(List<MediaItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isGrid ? R.layout.item_media_grid : R.layout.item_media_card;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvYear.setText(item.getYear());
        holder.tvRating.setText(item.getFormattedRating());

        String posterUrl = item.getFullPosterUrl();
        if (posterUrl != null) {
            Glide.with(context)
                    .load(posterUrl)
                    .transform(new CenterCrop(), new RoundedCorners(18))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_card_movie)
                    .into(holder.ivPoster);
        } else {
            holder.ivPoster.setImageResource(R.drawable.bg_card_movie);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPoster;
        TextView tvTitle;
        TextView tvYear;
        TextView tvRating;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.ivPoster);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvYear = itemView.findViewById(R.id.tvYear);
            tvRating = itemView.findViewById(R.id.tvRating);
        }
    }
}

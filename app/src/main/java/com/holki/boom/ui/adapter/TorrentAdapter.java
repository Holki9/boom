package com.holki.boom.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.holki.boom.R;
import com.holki.boom.model.TorrentItem;

import java.util.ArrayList;
import java.util.List;

public class TorrentAdapter extends RecyclerView.Adapter<TorrentAdapter.ViewHolder> {
    private final Context context;
    private List<TorrentItem> items = new ArrayList<>();

    public TorrentAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<TorrentItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_torrent, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TorrentItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvQuality.setText(item.getQualityTag());
        holder.tvVoice.setText(item.getVoiceTag());
        holder.tvSize.setText(item.getSize());
        holder.tvSeeders.setText("🟢 " + item.getSeeders() + "  🔴 " + item.getLeechers());

        // Play torrent (e.g. with TorrServe, VLC, Vimu, etc.)
        holder.btnPlay.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(item.getMagnetUrl()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                // If no app can handle magnet URI, copy to clipboard
                copyMagnet(item.getMagnetUrl());
                Toast.makeText(context, "Magnet-ссылка скопирована!", Toast.LENGTH_SHORT).show();
            }
        });

        // Copy magnet
        holder.btnCopy.setOnClickListener(v -> {
            copyMagnet(item.getMagnetUrl());
            Toast.makeText(context, "Magnet-ссылка скопирована в буфер!", Toast.LENGTH_SHORT).show();
        });
    }

    private void copyMagnet(String magnet) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Magnet", magnet));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvQuality;
        TextView tvVoice;
        TextView tvSize;
        TextView tvSeeders;
        View btnPlay;
        View btnCopy;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTorrentTitle);
            tvQuality = itemView.findViewById(R.id.tvQualityTag);
            tvVoice = itemView.findViewById(R.id.tvVoiceTag);
            tvSize = itemView.findViewById(R.id.tvTorrentSize);
            tvSeeders = itemView.findViewById(R.id.tvSeeders);
            btnPlay = itemView.findViewById(R.id.btnTorrentPlay);
            btnCopy = itemView.findViewById(R.id.btnTorrentCopy);
        }
    }
}

package com.aspada.localaudioplayer;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JournalAdapter extends RecyclerView.Adapter<JournalAdapter.ViewHolder> {
    private final List<PlaybackStateManager.State> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onFolderClick(String folderPath);
    }

    public JournalAdapter(List<PlaybackStateManager.State> items, OnItemClickListener listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journal, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaybackStateManager.State state = items.get(position);
        File folderFile = new File(state.folderPath);

        String folderName = folderFile.getName();
        String rootName = "(" + getRootFolderName(state.folderPath) + ")";

        holder.rootFolderText.setText(rootName);
        holder.folderNameText.setText(folderName);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFolderClick(state.folderPath);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Определяет имя корневой папки (Music, Downloads, Audiobooks) по полному пути.
     */
    private String getRootFolderName(String path) {
        if (path == null) return "";
        String lower = path.toLowerCase();
        if (lower.contains("/music/")) return "Music";
        if (lower.contains("/podcasts/")) return "Podcasts";
        if (lower.contains("/downloads/")) return "Downloads";
        if (lower.contains("/audiobooks/")) return "Audiobooks";
        // Fallback: первая папка после /storage/emulated/0/
        String external = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(external)) {
            String relative = path.substring(external.length() + 1);
            int slash = relative.indexOf('/');
            if (slash > 0) {
                return relative.substring(0, slash);
            }
        }
        return "Другое";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView folderNameText, rootFolderText;

        ViewHolder(View itemView) {
            super(itemView);
            folderNameText = itemView.findViewById(R.id.journal_folder_name);
            rootFolderText = itemView.findViewById(R.id.journal_root_folder);
        }
    }
}

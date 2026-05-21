package com.aspada.localaudioplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class RootFolderAdapter extends RecyclerView.Adapter<RootFolderAdapter.ViewHolder> {
    private final List<FolderItem> rootFolders;
    private final OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(FolderItem folder);
    }

    public RootFolderAdapter(List<FolderItem> rootFolders, OnFolderClickListener listener) {
        // Защита от null
        this.rootFolders = rootFolders != null ? rootFolders : new ArrayList<>();
        this.listener = listener;
    }

    /**
     * Обновить данные адаптера
     */
//    public void updateData(List<FolderItem> newFolders) {
//        this.rootFolders = newFolders != null ? newFolders : new ArrayList<>();
//        notifyDataSetChanged();
//    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FolderItem folder = rootFolders.get(position);
        String displayText = folder.getName() + " (" + countTotalFiles(folder) + ")";
        holder.textView.setText(displayText);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFolderClick(folder);
                v.setSelected(true);
            }
        });
    }

    private int countTotalFiles(FolderItem folder) {
        if (folder == null) return 0;
        List<AudioItem> curFiles = folder.getFiles();
        int count = curFiles != null ? curFiles.size() : 0;

        List<FolderItem> curFolders = folder.getFolders();
        if (curFolders != null) {
            for (FolderItem sub : curFolders) {
                count += countTotalFiles(sub);
            }
        }
        return count;
    }

    @Override
    public int getItemCount() {
        return rootFolders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}

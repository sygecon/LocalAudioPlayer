package com.aspada.localaudioplayer;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SubFolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_FOLDER = 0;
    private static final int TYPE_AUDIO = 1;
    private static int playingPosition = RecyclerView.NO_POSITION; // -1, если ничего не играет

    private final List<Object> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onFolderClick(FolderItem folder);
        void onAudioClick(FolderItem.AudioItem audio, int position, List<FolderItem.AudioItem> allAudioInFolder);
    }

    public SubFolderAdapter(FolderItem folder, OnItemClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();

        if (folder != null) {
            // Сначала подпапки
            List<FolderItem> curFolders = folder.getFolders();
            if (curFolders != null && ! curFolders.isEmpty()) {
                for (FolderItem sub : curFolders) {
                    if (sub != null) {
                        items.add(sub);
                    }
                }
            }
            // Потом аудиофайлы
            List<FolderItem.AudioItem> curFiles = folder.getFiles();
            if (curFiles != null && ! curFiles.isEmpty()) {
                for (FolderItem.AudioItem audio : curFiles) {
                    if (audio != null) {
                        items.add(audio);
                    }
                }
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof FolderItem ? TYPE_FOLDER : TYPE_AUDIO;
    }

    /**
     * @param parent   The ViewGroup into which the new View will be added after it is bound to
     *                 an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ViewHolder that holds a View of the given view type.
     * @see #getItemViewType(int)
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_FOLDER) {
            View view = inflater.inflate(R.layout.item_folder, parent, false);
            // Убедитесь, что layout_height = wrap_content
            return new FolderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_audio, parent, false);
            // Убедитесь, что layout_height = wrap_content
            return new AudioViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (item == null) return;
        String txt;

        if (holder instanceof FolderViewHolder && item instanceof FolderItem) {
            FolderItem folder = (FolderItem) item;
            FolderViewHolder fh = (FolderViewHolder) holder;

            txt = "📁 " + (folder.getName());
            fh.nameText.setText(txt);

            List<FolderItem.AudioItem> curFiles = folder.getFiles();
            int count = curFiles != null ? curFiles.size() : 0;
            txt = count + " файлов";
            fh.countText.setText(txt);

            fh.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onFolderClick(folder);
            });
        } else
        if (holder instanceof AudioViewHolder && item instanceof FolderItem.AudioItem) {

            FolderItem.AudioItem audio = (FolderItem.AudioItem) item;
            AudioViewHolder ah = (AudioViewHolder) holder;

            ah.titleText.setText(audio.title);
            ah.durationText.setText(AppUtils.formatDuration(audio.duration));
            // Устанавливаем состояние активации: true, если этот элемент играет
            ah.itemView.setActivated(position == playingPosition);

            // (Опционально) Меняем иконку на воспроизведение
            if (position == playingPosition) {
                ah.imgPlayFile.setImageResource(R.drawable.ic_playing); // нужна иконка play
                ah.titleText.setTypeface(null, Typeface.BOLD);
                ah.durationText.setTypeface(null, Typeface.BOLD);
            } else {
                ah.imgPlayFile.setImageResource(R.drawable.ic_audio_file);
                ah.titleText.setTypeface(null, Typeface.NORMAL);
                ah.durationText.setTypeface(null, Typeface.NORMAL);
            }

            ah.itemView.setOnClickListener(v -> {

                if (listener != null) {
                    List<FolderItem.AudioItem> folderAudio = new ArrayList<>();

                    for (Object obj : items) {
                        if (obj instanceof FolderItem.AudioItem) {
                            folderAudio.add((FolderItem.AudioItem) obj);
                            v.setSelected(true);
                        }
                    }
                    int audioPosition = folderAudio.indexOf(audio);
                    listener.onAudioClick(audio, audioPosition, folderAudio);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, countText;
        FolderViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.folder_name);
            countText = itemView.findViewById(R.id.file_count);
        }
    }

    static class AudioViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, durationText;
        ImageView imgPlayFile;

        AudioViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.audio_title);
            durationText = itemView.findViewById(R.id.audio_duration);
            imgPlayFile = itemView.findViewById(R.id.img_play_file);
        }
    }

    // Новый метод для установки текущего трека
    public void setPlayingPosition(int position) {
        int oldPos = playingPosition;
        playingPosition = position;
        // Перерисовываем только те элементы, которые изменились
        if (oldPos >= 0 && oldPos < items.size()) {
            notifyItemChanged(oldPos);
        }
        if (playingPosition >= 0 && playingPosition < items.size()) {
            notifyItemChanged(playingPosition);
        }
    }
}

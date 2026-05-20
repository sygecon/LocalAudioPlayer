package com.aspada.localaudioplayer;

import android.Manifest;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final Handler seekBarHandler = new Handler(Looper.getMainLooper());

    // храним текущий адаптер
    private static SubFolderAdapter currentAdapter = null;
    private static List<FolderItem> currentFolders = null;
    private static final int REQUEST_CODE   = 0;

    private static boolean curFolderIsRoot  = true;
    private static int curCountSubFolders   = 0;
    private static String curFolderPath     = "";
    private static String previousFolderPath= "";

    private static int mpTrackCount         = 0;
    private static int mpActiveIndex        = -1;
    private static long mpPosition          = 0;

    // флаг ручного перетаскивания
    private static boolean isUserSeeking    = false;

    private ProgressBar progressBar         = null;
    private TextView statusText             = null;
    private RecyclerView recyclerView       = null;


    private AudioCacheManager cacheManager  = null;
    private MediaController mediaController = null;
    private ListenableFuture<MediaController> controllerFuture = null;
    private PlaybackStateManager stateManager = null;

    private final Runnable updateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaController != null && !isUserSeeking) {
                long position = mediaController.getCurrentPosition();

                SeekBar seekBar = findViewById(R.id.rangeSeekBar);
                if (seekBar.getMax() < position) {
                    UpdateUIRange();
                }
                if (seekBar.getMax() > 0) {
                    seekBar.setProgress((int) position);
                }
                TextView currentTime = findViewById(R.id.currentTime);
                currentTime.setText(AppUtils.formatDuration(position));
            }
            seekBarHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestPermissions(
                new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                REQUEST_CODE
        );
        curFolderPath = "";

        recyclerView = findViewById(R.id.recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);

        cacheManager = new AudioCacheManager(this);
        currentFolders = new ArrayList<>(); // Инициализация пустым списком
        stateManager = new PlaybackStateManager(this);

        // Кнопка обновить
        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    saveCurrentPlaybackState();
                    mediaController.stop();
                }
            }
            forceRescan();
            showRootFolders();
        });

        // Btn Back
        findViewById(R.id.btn_back).setOnClickListener(v -> goToPrevious());

        // Btn Play Click
        findViewById(R.id.btnPlayPause).setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                } else {
                    int count = mediaController.getMediaItemCount();

                    if (count > 0) {
                        if (mpActiveIndex < 0 || mpActiveIndex >= count) {
                            mpActiveIndex = 0;
                        }
                        mediaController.play();
                    } else {
                        mpActiveIndex = -1;
                    }
                }
            }
        });

        // Предыдущий трек
        findViewById(R.id.btnBackward).setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.hasPreviousMediaItem()) {
                    if (mpActiveIndex > 0) {
                        mpActiveIndex--;
                    }
                    mediaController.seekToPreviousMediaItem();
                }
            }
        });

        // Следующий трек
        findViewById(R.id.btnForward).setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.hasNextMediaItem()) {
                    if (mpActiveIndex + 1 < mediaController.getMediaItemCount()) {
                        mpActiveIndex++;
                    }
                    mediaController.seekToNextMediaItem();
                }
            }
        });

        // Перехват кнопки «Назад»
        this.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mediaController != null) {
                    if (mediaController.isPlaying()) {
                        saveCurrentPlaybackState();
                        mediaController.stop();
                    }
                }

                if (! curFolderIsRoot) {
                    goToPrevious();
                } else {
                    Toast.makeText(MainActivity.this, "Возвращаемся Назад", Toast.LENGTH_SHORT).show();
                    // Чтобы закрыть Activity при необходимости:
                    setEnabled(false); // Отключаем colback, чтобы не зациклить
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Ползунок
        SeekBar seekBar = findViewById(R.id.rangeSeekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (mediaController != null) {
                    //pause
                    if (mediaController.isPlaying()) {
                        mediaController.setPlayWhenReady(false);
                    }
                    seekToTrack(seekBar.getProgress());
                }
            }
        });
        // Create the callback
        // Создаем токен для связи с вашим сервисом
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, AudioPlaybackService.class));

        // Строим контроллер
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                if (mpActiveIndex > -1 && mediaController.getCurrentMediaItem() != null) {
                    if (mpActiveIndex >= mediaController.getMediaItemCount()) {
                        mpActiveIndex = 0;
                    }
                    seekToTrack(mpPosition);
                } else {
                    setupControls();
                    // Плеер сам свяжет кнопки из XML с логикой mediaController
                    //binding.playerView.setPlayer(mediaController);
                    // Загружаем данные
                    loadAudioData();
                }
                UpdateUI();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("Media3", "Binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));

        // Очистка неактуальных папок при старте
        Thread thread = new Thread(() -> stateManager.cleanupOldStates());
        thread.start();
    }

    private void loadAudioData() {
        showLoading("Загрузка...");

        cacheManager.loadData(new AudioCacheManager.CacheCallback() {
            @Override
            public void onDataReady(List<FolderItem> folders, boolean fromCache) {
                hideLoading();

                // Всегда проверяем на null
                if (folders == null) {
                    folders = new ArrayList<>();
                }
                currentFolders = folders;
//                String message = fromCache ? "Загружено из кэша" : "Сканирование завершено";
//                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                showRootFolders();
                navigateToFolderByPath();
            }

            @Override
            public void onScanStarted() {
                statusText.setText("Сканирование файлов...");
            }

            @Override
            public void onScanProgress(String folderName) {
                statusText.setText(folderName);
            }

            @Override
            public void onScanComplete() {}
        });
    }

    private void forceRescan() {
        cacheManager.forceRescan(new AudioCacheManager.CacheCallback() {

            @Override
            public void onDataReady(List<FolderItem> folders, boolean fromCache) {
                hideLoading();
                currentFolders = folders != null ? folders : new ArrayList<>();
                showRootFolders();
//                Toast.makeText(MainActivity.this,
//                        "Сканирование завершено", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanStarted() {
                showLoading("");
            }

            @Override
            public void onScanProgress(String folderName) {
                statusText.setText(folderName);
            }

            @Override
            public void onScanComplete() {}
        });
    }

    private void showRootFolders() {
        setTitle("Аудиокниги");
        curFolderIsRoot     = true;
        curFolderPath       = "";
        previousFolderPath  = "";

        if (currentFolders.isEmpty()) {
            // Показываем сообщение, что папки не найдены
            statusText.setText("Аудиофайлы не найдены. Нажмите Обновить для сканирования.");
            statusText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }
        RootFolderAdapter adapter = new RootFolderAdapter(currentFolders, this::showSubFolder);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void showSubFolder(FolderItem folder) {
        if (folder == null) return;
        String path = folder.getPath();

        List<FolderItem> subFolders = folder.getFolders();
        curCountSubFolders = subFolders.size();
        curFolderIsRoot    = folder.getIsRoot();

        if (! path.isEmpty() && ! curFolderIsRoot) {
            if (! path.equalsIgnoreCase(curFolderPath)) {
                previousFolderPath = curFolderPath;
                curFolderPath = path;
                // Создаём плейлист для ExoPlayer
                createNewPlaylist(folder.getFiles());

                // После установки плейлиста:
                // Обновляем выделение в адаптере, если текущая папка совпадает с отображаемой
                if (currentAdapter != null && mpActiveIndex > -1) {
                    // Индекс в смешанном списке items = количество подпапок + startPosition
                    int indexInAdapter = curCountSubFolders + mpActiveIndex;
                    currentAdapter.setPlayingPosition(indexInAdapter);
                }
            }
        } else {
            if (mediaController != null) {
                if (mediaController.getMediaItemCount() > 0)  {
                    mediaController.clearMediaItems();
                }
            }
            curFolderPath = path;
        }
        setTitle(folder.getName());

        currentAdapter = new SubFolderAdapter(folder, new SubFolderAdapter.OnItemClickListener() {
            @Override
            public void onFolderClick(FolderItem subFolder) {
                previousFolderPath = subFolder.getPath();
                showSubFolder(subFolder);
            }

            @Override
            public void onAudioClick(AudioItem audio, int position, List<AudioItem> allAudio) {
                if (position < 0) return;
                if (mpActiveIndex == position) return;
                if (mediaController == null) return;

                if (mediaController.getMediaItemCount() > 0)  {
                    // При клике на трек обновляем выделение в адаптере
                    //if (currentAdapter != null) {
                        // position в allAudio совпадает с индексом в общей коллекции?
                        // Внимание: в смешанном списке items индекс audioItem может отличаться от position,
                        // поэтому лучше найти правильный индекс по объекту audio в списке items адаптера.
                        // Но у нас есть ссылка на audio, и мы можем передать её в адаптер для поиска индекса.
                        // Для простоты можно запомнить индекс position, если allAudio – это ссылка на folder.audioFiles,
                        // а в items они идут после подпапок, значит смещение равно количеству подпапок.
                        // Упростим: передадим в playFolder, а там вызовем метод адаптера для подсветки.
                    //}
                    playFolder(position);
                }
            }
        });
        recyclerView.setAdapter(currentAdapter);
    }

    /**
     * Просто переключается на трек в текущем плейлисте
     */
    private void seekToTrack(long position) {
        if (mediaController.isPlaying()) {
            mediaController.setPlayWhenReady(false);
        }
        int count = mediaController.getMediaItemCount();
        if (count < 1) return;

        mpPosition = position;
        if (mpPosition < 0) mpPosition = 0;
        if (mpActiveIndex >= count) mpActiveIndex = count - 1;

        mediaController.seekTo(mpActiveIndex, mpPosition);
        mediaController.play();

        if (! curFolderIsRoot && ! curFolderPath.isEmpty() && mpActiveIndex > -1) {
            new Thread(() -> {
                stateManager.saveMarkHistoryTrack(mpActiveIndex);
                stateManager.saveMarkHistoryPosition((int) mpPosition);
            }).start();
        }
    }

    private void playFolder(int startPosition) {
        mpActiveIndex = startPosition;
        mpPosition = 0;

        if (mediaController != null) {
            if (! curFolderPath.isEmpty()) {
                PlaybackState savedState = stateManager.getState(curFolderPath);

                if (savedState != null) {
                    int trackIndex = savedState.getTrackIndex();
                    // Проверяем, что индекс в пределах нового плейлиста
                    if (trackIndex == mpActiveIndex && trackIndex < mpTrackCount) {
                        mpPosition = savedState.getPositionMs();
                    }
                }
            }

            seekToTrack(mpPosition);
        }
    }

    /**
     * Создаёт новый плейлист для другой папки
     */
    private void createNewPlaylist(List<AudioItem> audioFiles) {
        mpActiveIndex = 0;
        mpPosition    = 0;
        mpTrackCount  = 0;
        if (audioFiles == null) return;

        if (mediaController == null) return;
        if (mediaController.isPlaying()) {
            saveCurrentPlaybackState();
            mediaController.stop();
        }
        if (mediaController.getMediaItemCount() > 0) {
            mediaController.clearMediaItems();
        }
        if (audioFiles.isEmpty()) return;

        int n = 0;
        List<MediaItem> items = new ArrayList<>();
        for (AudioItem audio : audioFiles) {
            items.add(createMediaItem(audio, n));
            n++;
        }
        mpTrackCount = items.size();

        if (mpTrackCount > 0) {
            boolean isOpen = false;

            if (! curFolderPath.isEmpty()) {
                Log.e("PlaybackState", "OpenPlayList-" + curFolderPath);
                PlaybackState savedState = stateManager.getState(curFolderPath);

                if (savedState != null) {
                    int trackIndex = savedState.getTrackIndex();
                    // Проверяем, что индекс в пределах нового плейлиста
                    if (trackIndex > -1 && trackIndex < mpTrackCount) {
                        mpPosition = savedState.getPositionMs();
                        mpActiveIndex = trackIndex;

                        mediaController.setMediaItems(items, mpActiveIndex, mpPosition);
                        mediaController.prepare();
                        mediaController.play();
                        isOpen = true;
                    }
                }
            }

            if (! isOpen) {
                mediaController.setMediaItems(items);
                mediaController.prepare();
            }

            if (! curFolderPath.isEmpty() && mpActiveIndex > -1) {
                new Thread(() -> {
                    stateManager.saveMarkHistoryPath(curFolderPath);
                    stateManager.saveMarkHistoryTrack(mpActiveIndex);
                    stateManager.saveMarkHistoryPosition((int) mpPosition);
                }).start();
            }
        }
    }

    private static MediaItem createMediaItem(AudioItem itemObj, int numTrack) {
        return new MediaItem.Builder()
            .setMediaId(String.valueOf(numTrack))
            .setUri(itemObj.getUrl())
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTrackNumber((numTrack + 1))
                    .setTitle(itemObj.getTitle())
                    .build())
            .build();
    }

    /**
     * Универсальный метод проверки по URL первого трека
     */

    private void showLoading(String message) {
        progressBar.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message.isEmpty() ? "Сканирование..." : message);
        recyclerView.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaController != null) {
            if (mediaController.isPlaying()) {
                saveCurrentPlaybackState();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mediaController != null) {
            if (mediaController.isPlaying()) {
                saveCurrentPlaybackState();
                mediaController.stop();
            }
            mediaController.clearMediaItems();
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        seekBarHandler.removeCallbacks(updateSeekBarRunnable);
        stateManager.saveNow();

        if (mediaController != null) {
            mediaController.release();
            mediaController = null;
        }
        mpActiveIndex       = -1;
        mpPosition          = 0;
        mpTrackCount        = 0;
        curFolderPath       = null;
        previousFolderPath  = null;

        if (currentFolders != null) {
            if (! currentFolders.isEmpty()) currentFolders.clear();
            currentFolders = null;
        }
        currentAdapter      = null;

        super.onDestroy();
    }

    private void UpdateUIRange() {
        long duration = mediaController.getDuration();

        if (duration > 0) {
            TextView totalTime = findViewById(R.id.totalTime);
            totalTime.setText(AppUtils.formatDuration(duration));

            SeekBar seekBar = findViewById(R.id.rangeSeekBar);
            seekBar.setMax((int) duration);
        }
    }

    private void UpdateUI() {
        String txt = "Трек " + (mpActiveIndex + 1) + " из " + mpTrackCount;
        TextView statTrack = findViewById(R.id.trackNumber);
        statTrack.setText(txt);

        UpdateUIRange();
    }

    /**
     * Устанавливаем События (Events) для Плейера
     */
    private void setupControls() {
        mediaController.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                // Обновляем текущий индекс
                if (mediaController != null) {
                    mpPosition = 0;
                    mpActiveIndex = mediaController.getCurrentMediaItemIndex();
                    // Обновляем выделение в адаптере
                    updateAdapterPlayingPosition(mpActiveIndex);
                    UpdateUI();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                ImageButton btn = findViewById(R.id.btnPlayPause);
                if (isPlaying) {
                    btn.setImageResource(R.drawable.pause_circle);
                    startSeekBarUpdates();
                } else {
                    stopSeekBarUpdates();
                    mpPosition = mediaController.getCurrentPosition();
                    btn.setImageResource(R.drawable.play_circle);
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
//                      Player.STATE_IDLE , Player.STATE_BUFFERING , Player.STATE_READY
                if (state == Player.STATE_ENDED) {
                    saveCurrentPlaybackState();
                    mpPosition = 0;
                }
            }
        });
    }

    //===================================================================
    private void startSeekBarUpdates() {
        stopSeekBarUpdates(); // на всякий случай
        seekBarHandler.post(updateSeekBarRunnable);
    }
    private void stopSeekBarUpdates() {
        seekBarHandler.removeCallbacks(updateSeekBarRunnable);
    }

    //================================================================================

    /**
     * Сохраняет текущее состояние воспроизведения для активной папки
     */
    private void saveCurrentPlaybackState() {
        if (mpActiveIndex == -1) return;
        if (curFolderIsRoot) return;
        if (curFolderPath.isEmpty()) return;

        if (mediaController != null) {
            long currentPosition = mediaController.getCurrentPosition();
            stateManager.saveState(curFolderPath, mpActiveIndex, currentPosition);

            new Thread(() -> {
                stateManager.saveMarkHistoryTrack(mpActiveIndex);
                stateManager.saveMarkHistoryPosition((int) currentPosition);
            }).start();
        }
    }

    private void updateAdapterPlayingPosition(int audioIndexInFolder) {
        if (currentAdapter != null && ! curFolderPath.isEmpty()) {

            int indexInAdapter = curCountSubFolders + audioIndexInFolder;
            currentAdapter.setPlayingPosition(indexInAdapter);

            new Thread(() -> {
                stateManager.saveMarkHistoryTrack(mpActiveIndex);
                stateManager.saveMarkHistoryPosition(0);
            }).start();
        }
    }

    /**
     * Находит папку по её полному пути во всём дереве currentFolders
     * и отображает её содержимое через showSubFolder.
     */
    private void navigateToFolderByPath() {
        if (currentFolders == null) return;
        if (currentFolders.isEmpty()) return;
        if (mediaController == null) return;

        String folderPath = stateManager.getMarkHistoryPath();
        if (folderPath.isEmpty()) return;

        FolderItem found = findFolderByPath(currentFolders, folderPath);

        if (found != null) {
            // Показываем содержимое найденной папки
            showSubFolder(found);

            int track = stateManager.getMarkHistoryTrack();
            if (mediaController.getMediaItemCount() > track) {
                long pos = stateManager.getMarkHistoryPosition();
                mpActiveIndex = track;

                seekToTrack(pos);
            }
        } else {
            // Опционально: сообщить пользователю
            Toast.makeText(this, "Папка не найдена: " + folderPath, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Рекурсивный поиск FolderItem по точному совпадению path.
     */
    private FolderItem findFolderByPath(List<FolderItem> folders, String targetPath) {
        if (folders == null || targetPath == null) return null;

        for (FolderItem folder : folders) {
            if (folder == null) continue;

            // Проверяем текущую папку
            if (targetPath.equals(folder.getPath())) {
                return folder;
            }

            // Рекурсивно ищем в подпапках
            List<FolderItem> subFolders = folder.getFolders();
            if (subFolders != null && ! subFolders.isEmpty()) {
                previousFolderPath = folder.getPath();

                FolderItem result  = findFolderByPath(subFolders, targetPath);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void goToPrevious() {

        if (mediaController != null) {
            if (mediaController.isPlaying()) {
                saveCurrentPlaybackState();
                mediaController.stop();
            }
        }
        Toast.makeText(MainActivity.this, "Возвращаемся Назад", Toast.LENGTH_SHORT).show();

        if (previousFolderPath.isEmpty()) {
            FolderItem prevFolder = findFolderByPath(currentFolders, previousFolderPath);
            if (prevFolder == null || prevFolder.getIsRoot()) {
                showRootFolders();
            } else {
                showSubFolder(prevFolder);
            }
        } else {
            if (!curFolderIsRoot) {
                showRootFolders();
            } else {
                finish();
            }
        }
    }

}
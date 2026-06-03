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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final Handler seekBarHandler = new Handler(Looper.getMainLooper());

    // храним текущий адаптер
    private static SubFolderAdapter currentAdapter = null;
    // сохраняем предыдущий адаптер
//    private static RecyclerView.Adapter previousAdapter = null;

    private static List<FolderItem> currentFolders = null;
    private static final int REQUEST_CODE   = 0;

    private boolean isJournalShowing        = false;
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
            if (mediaController != null && mediaController.getCurrentMediaItem() != null && !isUserSeeking) {
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
            seekBarHandler.postDelayed(this, 700);
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
            if (isJournalShowing) hideJournal();

            forceRescan();
            showRootFolders();
        });

        // Btn Back
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isJournalShowing) hideJournal();
            goToPrevious();
        });

        // Журнал
        ImageButton btnJournal = findViewById(R.id.btn_journal);
        btnJournal.setOnClickListener(v -> toggleJournal());

        // Btn Play Click
        findViewById(R.id.btnPlayPause).setOnClickListener(v -> {
            if (isJournalShowing) hideJournal();

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
            if (isJournalShowing) hideJournal();

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
            if (isJournalShowing) hideJournal();

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
                if (isJournalShowing) hideJournal();

                if (! curFolderIsRoot) {
                    goToPrevious();
                } else {
                    Toast.makeText(MainActivity.this, "Закрываем", Toast.LENGTH_SHORT).show();
                    setEnabled(false); // Отключаем colback, чтобы не зациклить
                    finish();
                    //getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Ползунок
        SeekBar seekBar = findViewById(R.id.rangeSeekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mpActiveIndex > -1 && !curFolderPath.isEmpty()) {
                    mpPosition = progress;
                    if (stateManager != null) {
                        stateManager.saveState(curFolderPath, mpActiveIndex, mpPosition);
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                if (isJournalShowing) hideJournal();
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaController != null && mediaController.getCurrentMediaItem() != null && isUserSeeking) {
                    int position = seekBar.getProgress();
                    mediaController.seekTo(position);
                }
                isUserSeeking = false;
            }
        });

        // Создаем токен для связи с сервисом
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, AudioPlaybackService.class));

        // Строим контроллер
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();

                int count = mediaController.getMediaItemCount();

                if (mpActiveIndex > -1 && mediaController.getCurrentMediaItem() != null && count > 0) {
                    if (mpActiveIndex >= count) mpActiveIndex = 0;
                    mediaController.seekTo(mpPosition);
                } else {
                    setupControls();
                    // Плеер свяжет кнопки с логикой mediaController
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
        if (stateManager != null) {
            stateManager.cleanupOldStates();
        }
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
                restoreLastSession();
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

        ImageButton btnJournal = findViewById(R.id.btn_journal);
        btnJournal.setImageResource(R.drawable.history_clock_gray_42);
    }

    private void showSubFolder(FolderItem folder) {
        if (folder == null) return;
        String path = folder.getPath();

        List<FolderItem> subFolders = folder.getFolders();
        curCountSubFolders = subFolders.size();
        curFolderIsRoot    = folder.getIsRoot();
        previousFolderPath = "";

        if (! path.isEmpty() && ! curFolderIsRoot) {
            if (!curFolderPath.isEmpty()) {
                if (! path.equalsIgnoreCase(curFolderPath)) {
                    previousFolderPath = curFolderPath;
                    curFolderPath = path;
                    // Создаём плейлист
                    createNewPlaylist(folder.getFiles());
                } else {
                    setAudioStatPosition(true);
                    if (mpActiveIndex < 0 || mpActiveIndex >= mediaController.getMediaItemCount()) {
                        mpActiveIndex = 0;
                    }
                    seekToTrack(mpPosition);
                    updateAdapterPlayingPosition(mpActiveIndex);
                }
            } else {
                previousFolderPath = "";
                curFolderPath = path;
                // Создаём плейлист
                createNewPlaylist(folder.getFiles());
            }
        } else {
            curFolderPath = "";
            if (mediaController != null) {
                if (mediaController.getMediaItemCount() > 0)  {
                    mediaController.clearMediaItems();
                }

                if (! path.isEmpty()) {
                    curFolderPath = path;
                    // Создаём плейлист
                    createNewPlaylist(folder.getFiles());
                }
            }
        }
        setTitle(folder.getName());

        currentAdapter = new SubFolderAdapter(folder, new SubFolderAdapter.OnItemClickListener() {
            @Override
            public void onFolderClick(FolderItem subFolder) {
                if (isJournalShowing) hideJournal();
                showSubFolder(subFolder);
            }

            /**
             * При клике на трек обновляем выделение в адаптере
             * Position в allAudio совпадает с индексом в общей коллекции?
             * Внимание: в смешанном списке items индекс audioItem может отличаться от position,
             * поэтому лучше найти правильный индекс по объекту audio в списке items адаптера.
             * Но у нас есть ссылка на audio, и мы можем передать её в адаптер для поиска индекса.
             * Для простоты можно запомнить индекс position, если allAudio – это ссылка на folder.audioFiles,
             * а в items они идут после подпапок, значит смещение равно количеству подпапок.
             */
            @Override
            public void onAudioClick(FolderItem.AudioItem audio, int position, List<FolderItem.AudioItem> allAudio) {
                if (isJournalShowing) hideJournal();
                if (currentAdapter == null) return;
                if (position < 0) return;
                if (mediaController == null) return;
                if (mediaController.getMediaItemCount() <= position) return;
                mpActiveIndex = position;
                mpPosition    = 0;
                int index     = 0;

                for (FolderItem.AudioItem item: allAudio) {
                    if (item.path.equalsIgnoreCase(audio.path)) {
                        mpActiveIndex = index;
                        break;
                    }
                    index++;
                }
                setAudioStatPosition(false);
                seekToTrack(mpPosition);

                if (!curFolderPath.isEmpty() && mpActiveIndex > -1) {
                    updateAdapterPlayingPosition(mpActiveIndex);

                    if (stateManager != null) {
                        stateManager.saveState(curFolderPath, mpActiveIndex, mpPosition);
                    }
                }
            }
        });
        recyclerView.setAdapter(currentAdapter);

        ImageButton btnJournal = findViewById(R.id.btn_journal);
        btnJournal.setImageResource(R.drawable.history_clock_gray_42);
    }

    private void setAudioStatPosition(boolean isChangeTrack) {
        if (! curFolderPath.isEmpty() && stateManager != null) {
            PlaybackStateManager.State savedState = stateManager.getState(curFolderPath);

            if (savedState != null) {
                int trackIndex = savedState.trackIndex;

                if (trackIndex > -1) {
                    if (isChangeTrack) {
                        mpActiveIndex = trackIndex;
                    }
                    if (mpActiveIndex == trackIndex) {
                        mpPosition = savedState.positionMs;
                    }
                }
            }
        }
    }

    /**
     * Переключается на трек в текущем плейлисте
     */
    private void seekToTrack(long position) {
        if (mpTrackCount < 1) return;
        long pos = position;
        if (pos < 0) pos = 0;

        if (mediaController.isPlaying()) {
            mediaController.setPlayWhenReady(false);
        }
        if (mpActiveIndex < 0) mpActiveIndex = 0;
        if (mpActiveIndex >= mpTrackCount) mpActiveIndex = mpTrackCount - 1;

        mediaController.seekTo(mpActiveIndex, pos);
        mediaController.play();
    }

    /**
     * Создаёт новый плейлист для папки
     */
    private void createNewPlaylist(List<FolderItem.AudioItem> audioFiles) {
        mpActiveIndex = -1;
        mpPosition    = 0;
        mpTrackCount  = 0;

        if (mediaController == null) return;
        if (mediaController.isPlaying()) {
            saveCurrentPlaybackState();
            mediaController.stop();
        }
        mediaController.clearMediaItems();

        if (audioFiles == null) return;
        if (audioFiles.isEmpty()) return;

        setAudioStatPosition(true);
        int n = 0;
        List<MediaItem> items = new ArrayList<>();

        for (FolderItem.AudioItem audio : audioFiles) {
            items.add(
                createMediaItem(audio, n)
            );
            n++;
        }
        mpTrackCount = items.size();
        if (mpTrackCount < 1) return;
        if (mpActiveIndex >= mpTrackCount || mpActiveIndex < 0) mpActiveIndex = 0;

        mediaController.setMediaItems(items, mpActiveIndex, mpPosition);
        mediaController.prepare();
        mediaController.play();

        if (mpActiveIndex > -1 && !curFolderPath.isEmpty()) {
            updateAdapterPlayingPosition(mpActiveIndex);

            if (stateManager != null) {
                stateManager.saveState(curFolderPath, mpActiveIndex, mpPosition);
            }
        }
    }

    private static MediaItem createMediaItem(FolderItem.AudioItem itemObj, int numTrack) {
        return new MediaItem.Builder()
            .setMediaId(String.valueOf(numTrack))
            .setUri(itemObj.path)
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setTrackNumber((numTrack + 1))
                    .setTitle(itemObj.title)
                    .build())
            .build();
    }

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
    protected void onResume() {
        super.onResume();
        // Небольшая задержка, чтобы макет перестроился
        recyclerView.post(this::scrollToActiveItem);
    }

    @Override
    public void onDestroy() {
        seekBarHandler.removeCallbacks(updateSeekBarRunnable);

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

        if (mediaController != null) {
            try {
                mediaController.release();
            } catch (Exception e) {
                mediaController = null;
            }
        }

        if (currentFolders != null) {
            if (! currentFolders.isEmpty()) currentFolders.clear();
        }
        super.onDestroy();
    }

    private void UpdateUIRange() {
        if (mpActiveIndex == -1) return;
        if (mediaController == null) return;
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
                mpPosition = 0;
                int index  = 0;
                // Обновляем текущий индекс
                if (mediaItem != null) {
                    index = Integer.parseInt(mediaItem.mediaId);// mediaController.getCurrentMediaItemIndex();

                    if (index > -1 && index < mediaController.getMediaItemCount()) {
                        if (stateManager != null && !curFolderPath.isEmpty()) {
                            stateManager.saveState(curFolderPath, index, mpPosition);
                        }
                    }
                } else {
                    mediaController.stop();
                }
                mpActiveIndex = index;
                // Обновляем выделение в адаптере
                updateAdapterPlayingPosition(mpActiveIndex);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                ImageButton btn = findViewById(R.id.btnPlayPause);
                if (isPlaying) {
                    btn.setImageResource(R.drawable.pause_circle);
                    startSeekBarUpdates();
                } else {
                    stopSeekBarUpdates();
                    btn.setImageResource(R.drawable.play_circle);

                    if (!curFolderPath.isEmpty() && mpActiveIndex > -1 && stateManager != null) {
                        mpPosition = mediaController.getCurrentPosition();

                        stateManager.saveState(curFolderPath, mpActiveIndex, mpPosition);
                    }
                }
            }

//            @Override
//            public void onPlaybackStateChanged(int state) {
//                      Player.STATE_IDLE , Player.STATE_BUFFERING , Player.STATE_READY
//                if (state == Player.STATE_ENDED) {
//                    mpPosition = 0;
//                    mpActiveIndex = -1;
//                    if (! curFolderPath.isEmpty()) {
//                        stateManager.removeState(curFolderPath);
//                    }
//                }
                // Срабатывает именно при player.stop()
//                else if (state == Player.STATE_IDLE) {
//                }
//            }
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

    /**
     * Сохраняет текущее состояние воспроизведения для активной папки
     */
    private void saveCurrentPlaybackState() {
        if (stateManager != null && !curFolderPath.isEmpty() && mpActiveIndex > -1) {
            mpPosition = mediaController.getCurrentPosition();
            stateManager.saveState(curFolderPath, mpActiveIndex, mpPosition);
        }
    }

    /**
     * После установки плейлиста:
     * Обновляем выделение в адаптере, если текущая папка совпадает с отображаемой
     */
    private void updateAdapterPlayingPosition(int audioIndexInFolder) {
        if (currentAdapter != null && ! curFolderPath.isEmpty()) {
            int indexInAdapter = curCountSubFolders + audioIndexInFolder;
            currentAdapter.setPlayingPosition(indexInAdapter);
            UpdateUI();
            // Прокручиваем к активному элементу
            scrollToActiveItem();
        }
    }

    /**
     * Находит папку по её полному пути во всём дереве currentFolders
     * и отображает её содержимое через showSubFolder.
     */
    private void navigateToFolderByPath(String folderPath) {
        if (currentFolders.isEmpty()) return;
        if (folderPath.isEmpty()) return;

        FolderItem found = findFolderByPath(currentFolders, folderPath);
        if (found != null) {
            // Показываем содержимое найденной папки
            showSubFolder(found);
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
            if (!curFolderIsRoot) showRootFolders();
        }
    }

    // Журнал
    private void toggleJournal() {
        if (isJournalShowing) {
            // Возвращаемся к основному виду
            hideJournal();
        } else {
            // Показываем журнал
            showJournal();
        }
    }

    private void showJournal() {
        if (stateManager == null) return;

        List<PlaybackStateManager.State> recent = stateManager.getRecentFolders(15);
        if (recent.isEmpty()) {
            Toast.makeText(this, "Журнал пуст", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mediaController != null) {
            if (mediaController.isPlaying()) {
                saveCurrentPlaybackState();
            }
        }

        previousFolderPath = curFolderPath; // может быть null, если были на корневом экране
        isJournalShowing = true;
        setTitle("Журнал");

        JournalAdapter journalAdapter = new JournalAdapter(recent, folderPath -> {
            // При клике на папку в журнале
            navigateToFolderByPath(folderPath);
            // После перехода автоматически возвращаемся к основному режиму
            hideJournal();
            UpdateUI();
        });
        recyclerView.setAdapter(journalAdapter);
        // Меняем иконку кнопки (если нужно)
        ImageButton btnJournal = findViewById(R.id.btn_journal);
        btnJournal.setImageResource(R.drawable.close_silver_42);
    }

    private void hideJournal() {
        isJournalShowing = false;

        // Восстанавливаем предыдущий вид
        if (currentAdapter != null) {
            recyclerView.setAdapter(currentAdapter);
            scrollToActiveItem();
        } else {
            showRootFolders();
        }
        // Восстанавливаем заголовок
//        if (! previousFolderPath.isEmpty()) {
//            setTitle(previousFolderPath);
//        } else {
//            setTitle("Аудиокниги");
//        }
        ImageButton btnJournal = findViewById(R.id.btn_journal);
        btnJournal.setImageResource(R.drawable.history_clock_gray_42);
        // btnJournal.setImageResource(R.drawable.ic_journal);
    }

    private void restoreLastSession() {
        PlaybackStateManager.State last = stateManager.getLastPlayedState();
        if (last == null) return;

        String folderPath = last.folderPath;
        if (folderPath != null && ! folderPath.isEmpty()) {
            File folder = new File(folderPath);
            if (folder.exists() && folder.isDirectory()) {
                navigateToFolderByPath(folderPath);
            }
        }
    }

    private void scrollToActiveItem() {
        if (!(recyclerView.getAdapter() instanceof SubFolderAdapter)) return;

        SubFolderAdapter adapter = (SubFolderAdapter) recyclerView.getAdapter();
        int position = adapter.getPlayingPosition();

        if (position != RecyclerView.NO_POSITION) {
            // Плавная прокрутка с небольшим смещением, чтобы элемент был в верхней трети экрана
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(position, recyclerView.getHeight() / 4);
            }
        }
    }
}
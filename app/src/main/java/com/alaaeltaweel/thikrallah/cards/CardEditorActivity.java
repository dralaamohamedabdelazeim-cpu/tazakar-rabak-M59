package com.alaaeltaweel.thikrallah.cards;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alaaeltaweel.thikrallah.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** شاشة تعديل الكارت: النصوص + الخلفية + الخط + اللون + الحجم، ثم مشاركة أو حفظ كصورة. */
public class CardEditorActivity extends AppCompatActivity {

    public static final String EXTRA_TEMPLATE_ID = "card_template_id";
    private static final String PREF_SENDER = "cards_sender_name";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<Integer, Bitmap> bgThumbs = new LruCache<Integer, Bitmap>(24);

    private CardState state;
    private boolean colorTouched = false;
    private boolean destroyed = false;
    private int renderToken = 0;

    private ImageView preview;
    private EditText editHeadline, editBody, editSender;
    private LinearLayout fontsContainer, colorsContainer, formatContainer;
    private RecyclerView recyclerBackgrounds;
    private BackgroundAdapter bgAdapter;
    private Button btnShare, btnSave;
    private SharedPreferences prefs;

    private final Runnable renderRunnable = new Runnable() {
        @Override
        public void run() {
            doRender();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_editor);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("تعديل البطاقة");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        final View root = findViewById(R.id.editor_root);
        final int baseBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottom + bars.bottom);
            return insets;
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        CardCatalog.Template t = CardCatalog.findTemplate(getIntent().getStringExtra(EXTRA_TEMPLATE_ID));
        if (t == null) t = CardCatalog.templateOfDay(this);
        state = CardState.fromTemplate(t, prefs.getString(PREF_SENDER, ""));

        preview = findViewById(R.id.img_preview);
        editHeadline = findViewById(R.id.edit_headline);
        editBody = findViewById(R.id.edit_body);
        editSender = findViewById(R.id.edit_sender);
        fontsContainer = findViewById(R.id.fonts_container);
        colorsContainer = findViewById(R.id.colors_container);
        formatContainer = findViewById(R.id.format_container);
        recyclerBackgrounds = findViewById(R.id.recycler_backgrounds);
        btnShare = findViewById(R.id.btn_share);
        btnSave = findViewById(R.id.btn_save);
        SeekBar seek = findViewById(R.id.seek_size);

        editHeadline.setText(state.headline);
        editBody.setText(state.body);
        editSender.setText(state.sender);

        editHeadline.addTextChangedListener(new SimpleWatcher() {
            @Override
            void changed(String text) {
                state.headline = text;
                scheduleRender();
            }
        });
        editBody.addTextChangedListener(new SimpleWatcher() {
            @Override
            void changed(String text) {
                state.body = text;
                scheduleRender();
            }
        });
        editSender.addTextChangedListener(new SimpleWatcher() {
            @Override
            void changed(String text) {
                state.sender = text;
                scheduleRender();
            }
        });

        seek.setProgress(30);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                state.sizeScale = 0.7f + progress / 100f;
                scheduleRender();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        recyclerBackgrounds.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        bgAdapter = new BackgroundAdapter();
        recyclerBackgrounds.setAdapter(bgAdapter);

        buildFormatChips();
        buildFontChips();
        buildColorSwatches();

        btnShare.setOnClickListener(v -> exportCard(true));
        btnSave.setOnClickListener(v -> exportCard(false));

        doRender();
    }

    @Override
    protected void onPause() {
        try {
            String name = state.sender == null ? "" : state.sender.trim();
            prefs.edit().putString(PREF_SENDER, name).apply();
        } catch (Exception ignored) { }
        super.onPause();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(renderRunnable);
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // المعاينة
    // ------------------------------------------------------------------

    private void scheduleRender() {
        main.removeCallbacks(renderRunnable);
        main.postDelayed(renderRunnable, 90);
    }

    private void doRender() {
        final CardState snapshot = state.copy();
        final int token = ++renderToken;
        try {
            executor.execute(() -> {
                try {
                    final Bitmap b = CardRenderer.render(CardEditorActivity.this, snapshot, 900);
                    main.post(() -> {
                        if (!destroyed && token == renderToken) preview.setImageBitmap(b);
                    });
                } catch (Throwable ignored) { }
            });
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------
    // الأزرار والاختيارات
    // ------------------------------------------------------------------

    private TextView makeChip(String text, boolean selected, View.OnClickListener l) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setBackgroundResource(R.drawable.card_chip_bg);
        int padH = dp(14);
        int padV = dp(8);
        chip.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        chip.setLayoutParams(lp);
        styleChip(chip, selected);
        chip.setOnClickListener(l);
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.page_background : R.color.text_primary));
    }

    private void buildFormatChips() {
        formatContainer.removeAllViews();
        formatContainer.addView(makeChip("مربع", !state.portrait, v -> {
            state.portrait = false;
            buildFormatChips();
            scheduleRender();
        }));
        formatContainer.addView(makeChip("طولي (ستوري)", state.portrait, v -> {
            state.portrait = true;
            buildFormatChips();
            scheduleRender();
        }));
    }

    private void buildFontChips() {
        fontsContainer.removeAllViews();
        for (int i = 0; i < CardCatalog.FONT_NAMES.length; i++) {
            final int idx = i;
            fontsContainer.addView(makeChip(CardCatalog.FONT_NAMES[i], state.fontIndex == i, v -> {
                state.fontIndex = idx;
                buildFontChips();
                scheduleRender();
            }));
        }
    }

    private void buildColorSwatches() {
        colorsContainer.removeAllViews();
        for (final int color : CardCatalog.TEXT_COLORS) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(dp(5), dp(4), dp(5), dp(4));
            swatch.setLayoutParams(lp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            boolean selected = state.textColor == color;
            gd.setStroke(dp(selected ? 3 : 1),
                    ContextCompat.getColor(this, selected ? R.color.brand_gold : R.color.card_stroke));
            swatch.setBackground(gd);
            swatch.setOnClickListener(v -> {
                state.textColor = color;
                colorTouched = true;
                buildColorSwatches();
                scheduleRender();
            });
            colorsContainer.addView(swatch);
        }
    }

    // ------------------------------------------------------------------
    // اختيار الخلفية
    // ------------------------------------------------------------------

    private final class BackgroundAdapter extends RecyclerView.Adapter<BackgroundAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final ImageView image;

            VH(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.img_bg_thumb);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card_background, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, final int position) {
            Bitmap b = bgThumbs.get(position);
            if (b == null) {
                b = CardRenderer.renderBackground(CardEditorActivity.this, position, 160);
                bgThumbs.put(position, b);
            }
            holder.image.setImageBitmap(b);
            holder.itemView.setBackgroundResource(position == state.backgroundIndex
                    ? R.drawable.card_thumb_selected : 0);
            holder.itemView.setContentDescription(CardCatalog.BACKGROUNDS[position].name);
            holder.itemView.setOnClickListener(v -> {
                state.backgroundIndex = position;
                if (!colorTouched) {
                    state.textColor = CardCatalog.BACKGROUNDS[position].defaultTextColor;
                    buildColorSwatches();
                }
                notifyDataSetChanged();
                scheduleRender();
            });
        }

        @Override
        public int getItemCount() {
            return CardCatalog.BACKGROUNDS.length;
        }
    }

    // ------------------------------------------------------------------
    // التصدير: مشاركة / حفظ
    // ------------------------------------------------------------------

    private void setBusy(boolean busy) {
        btnShare.setEnabled(!busy);
        btnSave.setEnabled(!busy);
        btnShare.setAlpha(busy ? 0.5f : 1f);
        btnSave.setAlpha(busy ? 0.5f : 1f);
    }

    private void exportCard(final boolean share) {
        setBusy(true);
        final CardState snapshot = state.copy();
        try {
            executor.execute(() -> {
                Bitmap bmp = null;
                File file = null;
                boolean saved = false;
                try {
                    bmp = CardRenderer.render(CardEditorActivity.this, snapshot, 1080);
                    if (share) {
                        file = writeToCache(bmp);
                    } else {
                        saved = saveToGallery(bmp);
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (bmp != null) bmp.recycle();
                }
                final File f = file;
                final boolean ok = saved;
                main.post(() -> {
                    if (destroyed) return;
                    setBusy(false);
                    if (share) {
                        if (f != null) startShare(f);
                        else toast("تعذّر تجهيز الصورة، حاول مرة أخرى");
                    } else {
                        toast(ok ? "تم حفظ البطاقة في معرض الصور ✅"
                                : "تعذّر الحفظ في المعرض، استخدم زر المشاركة");
                    }
                });
            });
        } catch (Exception e) {
            setBusy(false);
        }
    }

    private File writeToCache(Bitmap bmp) throws Exception {
        File dir = new File(getCacheDir(), "cards");
        if (!dir.exists()) dir.mkdirs();
        // تنظيف الصور القديمة (أقدم من يوم)
        File[] old = dir.listFiles();
        if (old != null) {
            long limit = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            for (File o : old) {
                if (o.lastModified() < limit) o.delete();
            }
        }
        File out = new File(dir, "card_" + System.currentTimeMillis() + ".png");
        FileOutputStream fos = new FileOutputStream(out);
        try {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } finally {
            fos.close();
        }
        return out;
    }

    private void startShare(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getString(R.string.file_authority), file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri("card", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "مشاركة البطاقة"));
        } catch (Exception e) {
            toast("تعذّرت المشاركة");
        }
    }

    private boolean saveToGallery(Bitmap bmp) {
        try {
            String name = "ThikrCard_" + System.currentTimeMillis() + ".png";
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Thikr Allah");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os == null) return false;
                try {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                } finally {
                    os.close();
                }
                v.clear();
                v.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, v, null, null);
                return true;
            } else {
                String result = MediaStore.Images.Media.insertImage(getContentResolver(), bmp, name, null);
                return result != null;
            }
        } catch (Throwable e) {
            return false;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        abstract void changed(String text);

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) { }

        @Override
        public void afterTextChanged(Editable s) {
            changed(s == null ? "" : s.toString());
        }
    }
}

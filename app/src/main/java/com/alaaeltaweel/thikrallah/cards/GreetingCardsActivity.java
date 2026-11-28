package com.alaaeltaweel.thikrallah.cards;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alaaeltaweel.thikrallah.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** شاشة بطاقات التهنئة: كارت اليوم + المناسبات + شبكة القوالب + "كروتي". */
public class GreetingCardsActivity extends AppCompatActivity {

    private static final String MINE = "__mine__";

    /** عنصر في الشبكة: قالب جاهز أو كارت محفوظ. */
    private static final class Item {
        final CardState state;
        final String templateId;   // لو قالب
        final String savedId;      // لو كارت محفوظ

        Item(CardState state, String templateId, String savedId) {
            this.state = state;
            this.templateId = templateId;
            this.savedId = savedId;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(60);
    private final List<Item> shown = new ArrayList<Item>();

    private TemplateAdapter adapter;
    private LinearLayout chipsContainer;
    private TextView emptyMine;
    private String selectedOccasion = null;   // null = الكل ، MINE = كروتي
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_greeting_cards);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("بطاقات التهنئة");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }

            final View root = findViewById(R.id.cards_root);
            final int baseBottom = root.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), baseBottom + bars.bottom);
                return insets;
            });

            emptyMine = findViewById(R.id.empty_mine);
            setupCardOfTheDay();

            chipsContainer = findViewById(R.id.chips_container);
            buildChips();

            RecyclerView recycler = findViewById(R.id.recycler_templates);
            recycler.setLayoutManager(new GridLayoutManager(this, 2));
            adapter = new TemplateAdapter();
            recycler.setAdapter(adapter);
            refreshItems();
        } catch (Throwable t) {
            // لو حصل أي خطأ نبيّنه بدل ما التطبيق يقفل بصمت
            Toast.makeText(this, "تعذّر فتح بطاقات التهنئة: " + t.getClass().getSimpleName()
                    + " - " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // رجّعنا من شاشة التعديل: ممكن يكون في كروت اتحفظت
        if (adapter != null && MINE.equals(selectedOccasion)) refreshItems();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // كارت اليوم
    // ------------------------------------------------------------------

    private void setupCardOfTheDay() {
        final CardCatalog.Template today = CardCatalog.templateOfDay(this);
        final ImageView img = findViewById(R.id.img_card_of_day);
        TextView caption = findViewById(R.id.card_of_day_caption);
        CardCatalog.Occasion occ = CardCatalog.findOccasion(today.occasionId);
        caption.setText("اضغط على الكارت لتعديله ومشاركته" + (occ != null ? "  •  " + occ.title : ""));

        executor.execute(() -> {
            try {
                final Bitmap b = CardRenderer.render(this, CardState.fromTemplate(today, ""), 720);
                main.post(() -> {
                    if (!destroyed) img.setImageBitmap(b);
                });
            } catch (Throwable ignored) { }
        });
        View container = findViewById(R.id.card_of_day_container);
        container.setOnClickListener(v -> openTemplate(today.id));
    }

    // ------------------------------------------------------------------
    // المناسبات
    // ------------------------------------------------------------------

    private void buildChips() {
        chipsContainer.removeAllViews();
        addChip("🎴 الكل", null);
        addChip("⭐ كروتي", MINE);
        for (CardCatalog.Occasion o : CardCatalog.OCCASIONS) {
            addChip(o.emoji + " " + o.title, o.id);
        }
        updateChipStates();
    }

    private void addChip(String text, final String occasionId) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setTag(occasionId == null ? "" : occasionId);
        chip.setBackgroundResource(R.drawable.card_chip_bg);
        int padH = dp(14);
        int padV = dp(8);
        chip.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            selectedOccasion = occasionId;
            updateChipStates();
            refreshItems();
        });
        chipsContainer.addView(chip);
    }

    private void updateChipStates() {
        for (int i = 0; i < chipsContainer.getChildCount(); i++) {
            TextView chip = (TextView) chipsContainer.getChildAt(i);
            String tag = String.valueOf(chip.getTag());
            boolean selected = selectedOccasion == null ? tag.length() == 0 : tag.equals(selectedOccasion);
            chip.setSelected(selected);
            chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.page_background : R.color.text_primary));
        }
    }

    private void refreshItems() {
        shown.clear();
        boolean mine = MINE.equals(selectedOccasion);
        if (mine) {
            for (MyCardsStore.Saved s : MyCardsStore.loadAll(this)) {
                shown.add(new Item(s.state, null, s.id));
            }
        } else if (selectedOccasion == null) {
            for (CardCatalog.Template t : CardCatalog.allTemplates()) {
                shown.add(new Item(CardState.fromTemplate(t, ""), t.id, null));
            }
        } else {
            CardCatalog.Occasion o = CardCatalog.findOccasion(selectedOccasion);
            if (o != null) {
                for (CardCatalog.Template t : o.templates) {
                    shown.add(new Item(CardState.fromTemplate(t, ""), t.id, null));
                }
            }
        }
        emptyMine.setVisibility(mine && shown.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------
    // فتح المحرر / الحذف
    // ------------------------------------------------------------------

    private void openTemplate(String templateId) {
        Intent i = new Intent(this, CardEditorActivity.class);
        i.putExtra(CardEditorActivity.EXTRA_TEMPLATE_ID, templateId);
        startActivity(i);
    }

    private void openSaved(String savedId) {
        Intent i = new Intent(this, CardEditorActivity.class);
        i.putExtra(CardEditorActivity.EXTRA_SAVED_ID, savedId);
        startActivity(i);
    }

    private void confirmDelete(final String savedId) {
        new AlertDialog.Builder(this)
                .setMessage("حذف هذا الكارت من كروتي؟")
                .setPositiveButton("حذف", (d, w) -> {
                    MyCardsStore.delete(this, savedId);
                    refreshItems();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // شبكة القوالب
    // ------------------------------------------------------------------

    private final class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.VH> {

        final class VH extends RecyclerView.ViewHolder {
            final ImageView image;

            VH(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.img_thumb);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card_template, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull final VH holder, int position) {
            final Item item = shown.get(position);
            final String key = item.state.signature();
            holder.image.setTag(key);
            holder.image.setScaleType(item.state.portrait ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
            Bitmap cached = thumbs.get(key);
            if (cached != null) {
                holder.image.setImageBitmap(cached);
            } else {
                holder.image.setImageDrawable(null);
                try {
                    executor.execute(() -> {
                        try {
                            final Bitmap b = CardRenderer.render(GreetingCardsActivity.this, item.state, 420);
                            thumbs.put(key, b);
                            main.post(() -> {
                                if (!destroyed && key.equals(holder.image.getTag())) {
                                    holder.image.setImageBitmap(b);
                                }
                            });
                        } catch (Throwable ignored) { }
                    });
                } catch (Exception ignored) { }
            }
            holder.itemView.setOnClickListener(v -> {
                if (item.savedId != null) openSaved(item.savedId);
                else openTemplate(item.templateId);
            });
            if (item.savedId != null) {
                holder.itemView.setOnLongClickListener(v -> {
                    confirmDelete(item.savedId);
                    return true;
                });
            } else {
                holder.itemView.setOnLongClickListener(null);
                holder.itemView.setLongClickable(false);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}

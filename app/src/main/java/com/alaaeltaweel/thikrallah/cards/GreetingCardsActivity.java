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

import androidx.annotation.NonNull;
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

/** شاشة بطاقات التهنئة: كارت اليوم + المناسبات + شبكة القوالب. */
public class GreetingCardsActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(48);
    private final List<CardCatalog.Template> shown = new ArrayList<CardCatalog.Template>();

    private TemplateAdapter adapter;
    private LinearLayout chipsContainer;
    private String selectedOccasion = null;   // null = الكل
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        setupCardOfTheDay();

        chipsContainer = findViewById(R.id.chips_container);
        buildChips();

        RecyclerView recycler = findViewById(R.id.recycler_templates);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new TemplateAdapter();
        recycler.setAdapter(adapter);
        refreshTemplates();
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
        container.setOnClickListener(v -> openEditor(today));
    }

    // ------------------------------------------------------------------
    // المناسبات
    // ------------------------------------------------------------------

    private void buildChips() {
        chipsContainer.removeAllViews();
        addChip("🎴 الكل", null);
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
            refreshTemplates();
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

    private void refreshTemplates() {
        shown.clear();
        if (selectedOccasion == null) {
            shown.addAll(CardCatalog.allTemplates());
        } else {
            CardCatalog.Occasion o = CardCatalog.findOccasion(selectedOccasion);
            if (o != null) shown.addAll(o.templates);
        }
        adapter.notifyDataSetChanged();
    }

    private void openEditor(CardCatalog.Template t) {
        Intent i = new Intent(this, CardEditorActivity.class);
        i.putExtra(CardEditorActivity.EXTRA_TEMPLATE_ID, t.id);
        startActivity(i);
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
            final CardCatalog.Template t = shown.get(position);
            holder.image.setTag(t.id);
            Bitmap cached = thumbs.get(t.id);
            if (cached != null) {
                holder.image.setImageBitmap(cached);
            } else {
                holder.image.setImageDrawable(null);
                try {
                    executor.execute(() -> {
                        try {
                            final Bitmap b = CardRenderer.render(GreetingCardsActivity.this,
                                    CardState.fromTemplate(t, ""), 420);
                            thumbs.put(t.id, b);
                            main.post(() -> {
                                if (!destroyed && t.id.equals(holder.image.getTag())) {
                                    holder.image.setImageBitmap(b);
                                }
                            });
                        } catch (Throwable ignored) { }
                    });
                } catch (Exception ignored) { }
            }
            holder.itemView.setOnClickListener(v -> openEditor(t));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}

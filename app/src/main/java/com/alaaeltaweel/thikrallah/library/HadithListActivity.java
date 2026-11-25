package com.alaaeltaweel.thikrallah.library;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/** أحاديث كتاب معيّن، أو نتائج بحث. الضغط على الحديث يفتحه كامل. */
public class HadithListActivity extends AppCompatActivity {

    private String bookId;
    private HadithData data;
    private ArrayList<Integer> indices = new ArrayList<>();
    private TextView statusText;
    private ListView listView;
    private int chapterId = -1;
    private String query;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bookId = getIntent().getStringExtra("book");
        if (bookId == null) {
            bookId = HadithData.BOOK_IDS[0];
        }
        chapterId = getIntent().getIntExtra("chapterId", -1);
        query = getIntent().getStringExtra("query");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(HadithData.bookName(bookId));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(LibUi.PAGE_BG);
        LibUi.rtl(root);

        statusText = LibUi.text(this, "جاري التحميل…", 16, LibUi.GREY);
        int p = LibUi.dp(this, 12);
        statusText.setPadding(p, p * 2, p, p);
        root.addView(statusText);

        listView = new ListView(this);
        listView.setDivider(new ColorDrawable(0x33D4AF37));
        listView.setDividerHeight(1);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HadithData d = null;
                ArrayList<Integer> found = new ArrayList<>();
                try {
                    d = HadithData.load(app, bookId);
                    if (query != null) {
                        found = d.search(query);
                    } else {
                        found = d.indicesInChapter(chapterId);
                    }
                } catch (Exception e) {
                    d = null;
                } catch (OutOfMemoryError e) {
                    d = null;
                }
                final HadithData fd = d;
                final ArrayList<Integer> ff = found;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        show(fd, ff);
                    }
                });
            }
        }).start();
    }

    private void show(HadithData d, ArrayList<Integer> found) {
        if (d == null) {
            statusText.setText("تعذّر فتح الكتاب. جرّب تقفل التطبيق وتفتحه تاني.");
            return;
        }
        data = d;
        indices = found;
        if (getSupportActionBar() != null) {
            if (query != null) {
                getSupportActionBar().setTitle("نتائج: " + query);
                String sub = HadithData.bookName(bookId) + " - " + found.size() + " نتيجة";
                if (found.size() >= HadithData.MAX_RESULTS) {
                    sub += " (أول " + HadithData.MAX_RESULTS + ")";
                }
                getSupportActionBar().setSubtitle(sub);
            } else {
                getSupportActionBar().setTitle(d.chapterName(chapterId));
                getSupportActionBar().setSubtitle(HadithData.bookName(bookId));
            }
        }
        if (found.isEmpty()) {
            statusText.setText("مفيش نتائج.");
            return;
        }
        statusText.setVisibility(View.GONE);

        final ArrayList<String> rows = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            int idx = found.get(i);
            String t = d.texts[idx];
            String preview = t.length() > 170 ? t.substring(0, 170) + "…" : t;
            rows.add("حديث رقم " + d.numbers[idx] + "\n" + preview);
        }
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(LibUi.WHITE);
                tv.setTextSize(16);
                tv.setTextDirection(View.TEXT_DIRECTION_RTL);
                int pad = LibUi.dp(getContext(), 12);
                tv.setPadding(pad, pad, pad, pad);
                return tv;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showHadith(indices.get(position));
            }
        });
    }

    private void showHadith(int idx) {
        final String title = HadithData.bookName(bookId) + " - حديث رقم " + data.numbers[idx];
        final String body = data.texts[idx];

        ScrollView sv = new ScrollView(this);
        TextView tv = LibUi.text(this, body, 20, LibUi.WHITE);
        tv.setLineSpacing(0f, 1.4f);
        tv.setTextIsSelectable(true);
        int pad = LibUi.dp(this, 16);
        tv.setPadding(pad, pad, pad, pad);
        sv.addView(tv);
        LibUi.rtl(sv);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("إغلاق", null)
                .setNeutralButton("نسخ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("hadith", body + "\n\n(" + title + ")"));
                            Toast.makeText(HadithListActivity.this, "تم النسخ", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("مشاركة", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_TEXT, body + "\n\n(" + title + ")");
                        startActivity(Intent.createChooser(share, "مشاركة الحديث"));
                    }
                })
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

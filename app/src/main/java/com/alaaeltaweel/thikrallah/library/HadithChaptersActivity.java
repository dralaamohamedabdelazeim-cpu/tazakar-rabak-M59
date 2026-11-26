package com.alaaeltaweel.thikrallah.library;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/** قائمة كتب (أبواب) البخاري أو مسلم + مربع بحث. */
public class HadithChaptersActivity extends AppCompatActivity {

    private String bookId;
    private HadithData data;
    private ListView listView;
    private TextView statusText;
    private EditText searchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LibUi.init(this);
        bookId = getIntent().getStringExtra("book");
        if (bookId == null) {
            bookId = HadithData.BOOK_IDS[0];
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(HadithData.bookName(bookId));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(LibUi.PAGE_BG);
        LibUi.rtl(root);
        int p = LibUi.dp(this, 8);
        root.setPadding(p, p, p, p);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        LibUi.rtl(searchRow);

        searchBox = new EditText(this);
        searchBox.setHint("ابحث في الكتاب (بدون تشكيل)");
        searchBox.setHintTextColor(LibUi.GREY);
        searchBox.setTextColor(LibUi.TEXT);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setTextDirection(View.TEXT_DIRECTION_RTL);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button searchBtn = LibUi.button(this, "بحث");
        searchRow.addView(searchBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = LibUi.text(this, "جاري تحميل الكتاب…", 16, LibUi.GREY);
        statusText.setPadding(p, p * 2, p, p);
        root.addView(statusText);

        listView = new ListView(this);
        listView.setDivider(new ColorDrawable(androidx.core.graphics.ColorUtils.setAlphaComponent(LibUi.GOLD, 0x33)));
        listView.setDividerHeight(1);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch();
            }
        });
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch();
                    return true;
                }
                return false;
            }
        });

        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HadithData d = null;
                try {
                    d = HadithData.load(app, bookId);
                } catch (Exception e) {
                    d = null;
                } catch (OutOfMemoryError e) {
                    d = null;
                }
                final HadithData result = d;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        showChapters(result);
                    }
                });
            }
        }).start();
    }

    private void showChapters(HadithData d) {
        if (d == null) {
            statusText.setText("تعذّر فتح الكتاب. جرّب تقفل التطبيق وتفتحه تاني.");
            return;
        }
        data = d;
        statusText.setVisibility(View.GONE);
        final ArrayList<String> rows = new ArrayList<>();
        for (int i = 0; i < d.chapterIds.length; i++) {
            rows.add(d.chapterNames[i] + "  (" + d.countInChapter(d.chapterIds[i]) + ")");
        }
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(LibUi.TEXT);
                tv.setTextSize(18);
                tv.setTextDirection(View.TEXT_DIRECTION_RTL);
                int pad = LibUi.dp(getContext(), 14);
                tv.setPadding(pad, pad, pad, pad);
                return tv;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(HadithChaptersActivity.this, HadithListActivity.class);
                i.putExtra("book", bookId);
                i.putExtra("chapterId", data.chapterIds[position]);
                startActivity(i);
            }
        });
    }

    private void doSearch() {
        String q = searchBox.getText().toString().trim();
        if (q.length() < 2) {
            Toast.makeText(this, "اكتب كلمة للبحث (حرفين على الأقل)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (data == null) {
            Toast.makeText(this, "استنى لحد ما الكتاب يتحمّل", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, HadithListActivity.class);
        i.putExtra("book", bookId);
        i.putExtra("query", q);
        startActivity(i);
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

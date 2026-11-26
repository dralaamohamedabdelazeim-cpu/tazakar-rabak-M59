package com.alaaeltaweel.thikrallah.library;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/** المكتبة: الأحاديث (جوه التطبيق) + التفسير (يتحمّل مرة واحدة ثم يشتغل بدون إنترنت). */
public class LibraryActivity extends AppCompatActivity {

    private LinearLayout root;
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private AlertDialog progressDialog;
    private ProgressBar bar;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LibUi.init(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("المكتبة");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(LibUi.PAGE_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = LibUi.dp(this, 12);
        root.setPadding(p, p, p, p);
        LibUi.rtl(root);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    protected void onDestroy() {
        cancel.set(true);
        super.onDestroy();
    }

    private void build() {
        root.removeAllViews();
        int gap = LibUi.dp(this, 6);

        root.addView(LibUi.sectionTitle(this, "الأحاديث النبوية (داخل التطبيق)"));
        for (int i = 0; i < HadithData.BOOK_IDS.length; i++) {
            final String id = HadithData.BOOK_IDS[i];
            Button b = LibUi.button(this, HadithData.BOOK_NAMES[i]);
            b.setTextSize(18);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, LibUi.dp(this, 64));
            lp.topMargin = gap;
            root.addView(b, lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent in = new Intent(LibraryActivity.this, HadithChaptersActivity.class);
                    in.putExtra("book", id);
                    startActivity(in);
                }
            });
        }

        root.addView(LibUi.sectionTitle(this, "التفسير"));
        TextView hint = LibUi.text(this,
                "كل تفسير بتحمّله مرة واحدة من الإنترنت، وبعدها يشتغل بدون إنترنت. "
                        + "بعد التحميل تقدر تفتح تفسير أي آية من المصحف بالضغط على الآية واختيار أيقونة التفسير.",
                14, LibUi.GREY);
        hint.setPadding(LibUi.dp(this, 4), 0, LibUi.dp(this, 4), gap);
        root.addView(hint);

        for (final String[] e : TafsirStore.EDITIONS) {
            final String slug = e[0];
            final String name = e[1];
            final boolean installed = TafsirStore.isInstalled(this, slug);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(LibUi.PANEL_BG);
            int pad = LibUi.dp(this, 10);
            row.setPadding(pad, pad, pad, pad);
            LibUi.rtl(row);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView title = LibUi.text(this, name, 17, LibUi.TEXT);
            info.addView(title);
            String stateText = "لم يُحمَّل بعد";
            if (installed) {
                long mb = TafsirStore.sizeOnDisk(this, slug) / (1024 * 1024);
                stateText = "تم التحميل (" + mb + " ميجا)";
            }
            info.addView(LibUi.text(this, stateText, 13, installed ? LibUi.GOLD : LibUi.GREY));
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button main = LibUi.button(this, installed ? "فتح" : "تحميل");
            row.addView(main, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            main.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (installed) {
                        Intent in = new Intent(LibraryActivity.this, TafsirActivity.class);
                        in.putExtra("sura", 1);
                        in.putExtra("ayah", 1);
                        in.putExtra("edition", slug);
                        startActivity(in);
                    } else {
                        confirmDownload(slug, name);
                    }
                }
            });
            if (installed) {
                Button del = LibUi.button(this, "حذف");
                row.addView(del, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                del.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmDelete(slug, name);
                    }
                });
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = gap;
            root.addView(row, lp);
        }

        TextView source = LibUi.text(this,
                "المصادر: نصوص الأحاديث من موقع سنة (sunnah.com) عن طريق مشروع hadith-json، "
                        + "ونصوص التفاسير من مشروع tafsir_api (المصدر الأصلي: موقع القرآن quran.com).",
                12, LibUi.GREY);
        source.setPadding(LibUi.dp(this, 4), LibUi.dp(this, 20), LibUi.dp(this, 4), LibUi.dp(this, 8));
        root.addView(source);
    }

    private void confirmDownload(final String slug, final String name) {
        new AlertDialog.Builder(this)
                .setTitle("تحميل " + name)
                .setMessage("التحميل بيحتاج إنترنت ومساحة على الموبايل، وبيتم مرة واحدة بس. "
                        + "لو التحميل وقف في النص هيكمّل من حيث وقف.")
                .setPositiveButton("تحميل", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDownload(slug, name);
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void confirmDelete(final String slug, final String name) {
        new AlertDialog.Builder(this)
                .setTitle("حذف " + name)
                .setMessage("هيتمسح من الموبايل، وتقدر تحمّله تاني في أي وقت.")
                .setPositiveButton("حذف", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TafsirStore.delete(LibraryActivity.this, slug);
                        build();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void startDownload(final String slug, String name) {
        cancel.set(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LibUi.dp(this, 20);
        box.setPadding(pad, pad, pad, pad);
        LibUi.rtl(box);
        statusText = LibUi.text(this, "جاري التحضير…", 15, LibUi.TEXT);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(TafsirStore.TOTAL_SURAS);
        box.addView(statusText);
        box.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressDialog = new AlertDialog.Builder(this)
                .setTitle("تحميل " + name)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("إيقاف", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancel.set(true);
                    }
                })
                .create();
        progressDialog.show();

        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                TafsirStore.download(app, slug, cancel, new TafsirStore.Listener() {
                    @Override
                    public void onProgress(int done, int total) {
                        bar.setProgress(done);
                        statusText.setText("تم تحميل " + done + " من " + total + " سورة");
                    }

                    @Override
                    public void onFinished(boolean ok, String message) {
                        if (progressDialog != null && progressDialog.isShowing() && !isFinishing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(app, message, Toast.LENGTH_LONG).show();
                        if (!isFinishing()) {
                            build();
                        }
                    }
                });
            }
        }).start();
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

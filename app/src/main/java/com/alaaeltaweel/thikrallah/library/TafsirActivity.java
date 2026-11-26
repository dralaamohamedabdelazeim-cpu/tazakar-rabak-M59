package com.alaaeltaweel.thikrallah.library;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alaaeltaweel.thikrallah.R;

import java.util.ArrayList;
import java.util.TreeMap;

/** قراءة تفسير آية من التفاسير اللي اتحمّلت على الجهاز (بدون إنترنت). */
public class TafsirActivity extends AppCompatActivity {

    private int sura = 1;
    private int ayah = 1;
    private String slug;
    private ArrayList<String> slugs = new ArrayList<>();
    private TreeMap<Integer, String> current;

    private TextView header;
    private TextView body;
    private ScrollView scroll;
    private Spinner spinner;
    private boolean spinnerReady = false;
    private int requestId = 0;
    private float textSp = 20f;
    private SharedPreferences prefs;
    private String[] suraNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LibUi.init(this);
        prefs = getSharedPreferences("library_prefs", MODE_PRIVATE);
        suraNames = getResources().getStringArray(R.array.sura_names);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("التفسير");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        sura = getIntent().getIntExtra("sura", 1);
        ayah = getIntent().getIntExtra("ayah", 1);
        if (sura < 1 || sura > TafsirStore.TOTAL_SURAS) {
            sura = 1;
        }
        if (ayah < 1 || ayah > TafsirStore.AYAH_COUNTS[sura - 1]) {
            ayah = 1;
        }
        textSp = prefs.getFloat("tafsir_text_sp", 20f);

        slugs = TafsirStore.installedSlugs(this);
        if (slugs.isEmpty()) {
            showEmpty();
            return;
        }
        String want = getIntent().getStringExtra("edition");
        if (want == null || !slugs.contains(want)) {
            want = prefs.getString("tafsir_edition", slugs.get(0));
        }
        if (!slugs.contains(want)) {
            want = slugs.get(0);
        }
        slug = want;
        buildReader();
        go(sura, ayah);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (slug == null && !TafsirStore.installedSlugs(this).isEmpty()) {
            recreate();
        }
    }

    private void showEmpty() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(LibUi.PAGE_BG);
        root.setGravity(android.view.Gravity.CENTER);
        LibUi.rtl(root);
        int p = LibUi.dp(this, 24);
        root.setPadding(p, p, p, p);

        TextView msg = LibUi.text(this,
                "لسه ما حمّلتش أي تفسير.\nافتح المكتبة وحمّل التفسير اللي تحبه مرة واحدة، وبعدها يشتغل بدون إنترنت.",
                17, LibUi.TEXT);
        msg.setGravity(android.view.Gravity.CENTER);
        root.addView(msg);

        Button open = LibUi.button(this, "فتح المكتبة");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = p;
        root.addView(open, lp);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TafsirActivity.this, LibraryActivity.class));
            }
        });
        setContentView(root);
    }

    private void buildReader() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(LibUi.PAGE_BG);
        LibUi.rtl(root);
        int p = LibUi.dp(this, 8);
        root.setPadding(p, p, p, p);

        // اختيار التفسير
        ArrayList<String> names = new ArrayList<>();
        for (String s : slugs) {
            names.add(TafsirStore.editionName(s));
        }
        spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(LibUi.GOLD);
                tv.setTextSize(17);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextSize(17);
                int pad = LibUi.dp(getContext(), 12);
                tv.setPadding(pad, pad, pad, pad);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(slugs.indexOf(slug));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnerReady) {
                    spinnerReady = true;
                    return;
                }
                String chosen = slugs.get(position);
                if (!chosen.equals(slug)) {
                    slug = chosen;
                    prefs.edit().putString("tafsir_edition", slug).apply();
                    go(sura, ayah);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        root.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header = LibUi.text(this, "", 18, LibUi.GOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(p, p, p, p);
        root.addView(header);

        scroll = new ScrollView(this);
        body = LibUi.text(this, "", textSp, LibUi.TEXT);
        body.setLineSpacing(0f, 1.4f);
        body.setTextIsSelectable(true);
        int pad = LibUi.dp(this, 12);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        LibUi.rtl(nav);
        Button prev = LibUi.button(this, "السابقة");
        Button pick = LibUi.button(this, "اختيار السورة");
        Button next = LibUi.button(this, "التالية");
        nav.addView(prev, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(pick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        nav.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ayah > 1) {
                    go(sura, ayah - 1);
                } else if (sura > 1) {
                    go(sura - 1, Integer.MAX_VALUE);
                }
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ayah < TafsirStore.AYAH_COUNTS[sura - 1]) {
                    go(sura, ayah + 1);
                } else if (sura < TafsirStore.TOTAL_SURAS) {
                    go(sura + 1, 1);
                }
            }
        });
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseSura();
            }
        });
    }

    /** يحمّل السورة في خيط خلفي ثم يعرض الآية. ayah = MAX_VALUE تعني آخر آية في السورة. */
    private void go(final int s, final int a) {
        final int req = ++requestId;
        header.setText("جاري التحميل…");
        final Context app = getApplicationContext();
        final String useSlug = slug;
        new Thread(new Runnable() {
            @Override
            public void run() {
                TreeMap<Integer, String> m = null;
                String err = null;
                try {
                    m = TafsirStore.loadSura(app, useSlug, s);
                } catch (Exception e) {
                    err = "تعذّر قراءة التفسير. جرّب تحذفه من المكتبة وتحمّله تاني.";
                } catch (OutOfMemoryError e) {
                    err = "الذاكرة مش كافية لفتح السورة دي.";
                }
                final TreeMap<Integer, String> fm = m;
                final String ferr = err;
                final int fa = (a == Integer.MAX_VALUE) ? TafsirStore.AYAH_COUNTS[s - 1] : a;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (req != requestId || isFinishing()) {
                            return;
                        }
                        show(s, fa, fm, ferr);
                    }
                });
            }
        }).start();
    }

    private void show(int s, int a, TreeMap<Integer, String> map, String err) {
        sura = s;
        ayah = a;
        current = map;
        header.setText("سورة " + suraNames[s - 1] + " - آية " + a);
        if (err != null || map == null) {
            body.setText(err == null ? "تعذّر قراءة التفسير." : err);
            return;
        }
        String text = map.get(a);
        if (text != null && text.trim().length() > 0) {
            body.setText(text);
        } else {
            // الآية ما ليهاش تفسير لوحدها: غالباً اتفسّرت مع آية قبلها
            int from = -1;
            for (int i = a - 1; i >= 1; i--) {
                String t = map.get(i);
                if (t != null && t.trim().length() > 0) {
                    from = i;
                    break;
                }
            }
            if (from > 0) {
                body.setText("تفسير هذه الآية جاء ضمن تفسير الآية " + from + ":\n\n" + map.get(from));
            } else {
                body.setText("مفيش تفسير لهذه الآية في هذا الكتاب.");
            }
        }
        body.setTextSize(textSp);
        scroll.scrollTo(0, 0);
    }

    private void chooseSura() {
        String[] items = new String[TafsirStore.TOTAL_SURAS];
        for (int i = 0; i < items.length; i++) {
            items[i] = (i + 1) + ". " + suraNames[i];
        }
        new AlertDialog.Builder(this)
                .setTitle("اختر السورة")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        askAyah(which + 1);
                    }
                })
                .show();
    }

    private void askAyah(final int s) {
        final int max = TafsirStore.AYAH_COUNTS[s - 1];
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("1");
        input.setSelectAllOnFocus(true);
        input.setTextColor(LibUi.TEXT);
        new AlertDialog.Builder(this)
                .setTitle("سورة " + suraNames[s - 1] + " - رقم الآية (1 إلى " + max + ")")
                .setView(input)
                .setPositiveButton("اذهب", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int a = 1;
                        try {
                            a = Integer.parseInt(input.getText().toString().trim());
                        } catch (NumberFormatException e) {
                            a = 1;
                        }
                        if (a < 1) {
                            a = 1;
                        }
                        if (a > max) {
                            a = max;
                        }
                        go(s, a);
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (slug != null) {
            menu.add(0, 1, 0, "A+").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, 2, 1, "A-").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == 1 || id == 2) {
            textSp += (id == 1) ? 2f : -2f;
            if (textSp < 14f) {
                textSp = 14f;
            }
            if (textSp > 40f) {
                textSp = 40f;
            }
            prefs.edit().putFloat("tafsir_text_sp", textSp).apply();
            if (body != null) {
                body.setTextSize(textSp);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

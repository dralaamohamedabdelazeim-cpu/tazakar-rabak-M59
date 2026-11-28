package com.alaaeltaweel.thikrallah.cards;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * بيانات بطاقات التهنئة: المناسبات والقوالب والخلفيات والخطوط والألوان.
 * كل التصاميم بتترسم بالكود (من غير صور) عشان تبقى خفيفة وبدون مشاكل حقوق.
 */
public final class CardCatalog {

    private CardCatalog() {}

    // ------------------------------------------------------------------
    // الخلفيات
    // ------------------------------------------------------------------
    public static final int ORN_MOON = 1;
    public static final int ORN_STARS = 2;
    public static final int ORN_MOSQUE = 4;
    public static final int ORN_LANTERNS = 8;
    public static final int ORN_GEO = 16;
    public static final int ORN_DOTS = 32;

    public static final class Background {
        public final String name;
        public final int top;
        public final int bottom;
        public final int ornaments;
        public final int ornamentColor;
        public final int frameColor;
        public final int defaultTextColor;

        Background(String name, int top, int bottom, int ornaments, int ornamentColor,
                   int frameColor, int defaultTextColor) {
            this.name = name;
            this.top = top;
            this.bottom = bottom;
            this.ornaments = ornaments;
            this.ornamentColor = ornamentColor;
            this.frameColor = frameColor;
            this.defaultTextColor = defaultTextColor;
        }
    }

    // ألوان النص
    public static final int C_WHITE = 0xFFFFFFFF;
    public static final int C_CREAM = 0xFFFFF3D6;
    public static final int C_GOLD = 0xFFE8C766;
    public static final int C_TEAL = 0xFF1F5E59;
    public static final int C_GREEN = 0xFF14502F;
    public static final int C_ROSE = 0xFF7A2B45;
    public static final int C_BLUE = 0xFF1F4E79;
    public static final int C_BROWN = 0xFF43341F;
    public static final int C_NAVY = 0xFF0B1120;
    public static final int C_BLACK = 0xFF000000;

    public static final int[] TEXT_COLORS = {
            C_WHITE, C_CREAM, C_GOLD, C_TEAL, C_GREEN, C_ROSE, C_BLUE, C_BROWN, C_NAVY, C_BLACK
    };

    public static final Background[] BACKGROUNDS = {
            /* 0  */ new Background("ليل ذهبي", 0xFF0B1120, 0xFF1E2E50, ORN_MOON | ORN_STARS, 0xFFD4AF37, 0xFFD4AF37, C_GOLD),
            /* 1  */ new Background("أخضر ملكي", 0xFF0D4A28, 0xFF1F7A4F, ORN_GEO, 0xFFD4AF37, 0xFFD4AF37, C_GOLD),
            /* 2  */ new Background("فوانيس", 0xFF1F5E59, 0xFF3FA59B, ORN_LANTERNS, 0xFFFFE08A, 0xFFFFE08A, C_WHITE),
            /* 3  */ new Background("فجر دافئ", 0xFFFFF1D0, 0xFFF0B86E, ORN_MOSQUE, 0xFF8A5A1C, 0xFF8A5A1C, C_BROWN),
            /* 4  */ new Background("بنفسجي قمري", 0xFF1B1F3B, 0xFF5B3A86, ORN_MOON | ORN_STARS, 0xFFFFE08A, 0xFFFFE08A, C_WHITE),
            /* 5  */ new Background("أبيض هادئ", 0xFFFFFFFF, 0xFFE3F2F0, ORN_GEO, 0xFF2A7771, 0xFF2A7771, C_TEAL),
            /* 6  */ new Background("وردي", 0xFFFFE6EE, 0xFFFFB9CF, ORN_DOTS, 0xFFFFFFFF, 0xFFD9718F, C_ROSE),
            /* 7  */ new Background("سماوي", 0xFFE3F4FF, 0xFF9CD2F5, ORN_DOTS, 0xFFFFFFFF, 0xFF3A86B8, C_BLUE),
            /* 8  */ new Background("ذهبي فاخر", 0xFF2B1D05, 0xFFB98C25, ORN_GEO, 0xFFFFE08A, 0xFFFFE08A, C_CREAM),
            /* 9  */ new Background("سماء النجوم", 0xFF0A1330, 0xFF16305E, ORN_STARS, 0xFFFFFFFF, 0xFFD4AF37, C_GOLD),
            /* 10 */ new Background("حديقة", 0xFFE9F7EC, 0xFFB5DEC1, ORN_DOTS, 0xFFFFFFFF, 0xFF2F7D4F, C_GREEN),
            /* 11 */ new Background("مسجد الليل", 0xFF101A33, 0xFF2A3F73, ORN_MOON | ORN_STARS | ORN_MOSQUE, 0xFFD4AF37, 0xFFD4AF37, C_CREAM),
    };

    // ------------------------------------------------------------------
    // الخطوط
    // ------------------------------------------------------------------
    public static final String[] FONT_NAMES = {"نسخ", "عثماني", "مصحف", "النظام"};
    public static final String[] FONT_ASSETS = {
            "fonts_2/DroidNaskh.ttf",
            "fonts_2/UthmanTN1 Ver10.otf",
            "uthmanic_hafs_ver12.otf",
            null
    };

    // ------------------------------------------------------------------
    // المناسبات والقوالب
    // ------------------------------------------------------------------
    public static final class Template {
        public final String id;
        public final String occasionId;
        public final String headline;
        public final String body;
        public final int backgroundIndex;
        public final int textColor;

        Template(String id, String occasionId, String headline, String body, int backgroundIndex, int textColor) {
            this.id = id;
            this.occasionId = occasionId;
            this.headline = headline;
            this.body = body;
            this.backgroundIndex = backgroundIndex;
            this.textColor = textColor;
        }
    }

    public static final class Occasion {
        public final String id;
        public final String title;
        public final String emoji;
        public final List<Template> templates = new ArrayList<Template>();

        Occasion(String id, String title, String emoji) {
            this.id = id;
            this.title = title;
            this.emoji = emoji;
        }
    }

    public static final List<Occasion> OCCASIONS = new ArrayList<Occasion>();
    private static Occasion cur;

    private static void occ(String id, String title, String emoji) {
        cur = new Occasion(id, title, emoji);
        OCCASIONS.add(cur);
    }

    private static void t(String headline, String body, int bg, int color) {
        cur.templates.add(new Template(cur.id + "_" + (cur.templates.size() + 1), cur.id,
                headline, body, bg, color));
    }

    static {
        occ("ramadan", "رمضان", "🌙");
        t("رمضان كريم", "كل عام وأنتم بخير، تقبل الله منا ومنكم الصيام والقيام", 0, C_GOLD);
        t("أهلاً رمضان", "اللهم بلّغنا رمضان، وأعنّا على صيامه وقيامه وحسن عبادته", 2, C_WHITE);
        t("رمضان مبارك", "شهر خير وبركة ومغفرة، جعله الله شهر عزٍّ وفرحٍ لكم ولأحبابكم", 1, C_GOLD);
        t("تقبل الله طاعتكم", "صيامًا مقبولًا وقيامًا مبرورًا وذنبًا مغفورًا", 4, C_CREAM);
        t("العشر الأواخر", "اللهم إنك عفوٌّ كريمٌ تحب العفو فاعفُ عنا", 9, C_GOLD);

        occ("eid_fitr", "عيد الفطر", "🎉");
        t("عيد فطر مبارك", "تقبل الله طاعاتكم، وكل عام وأنتم بخير", 1, C_GOLD);
        t("كل عام وأنتم بخير", "عيدكم مبارك وأيامكم سعيدة، جعله الله عيد فرحٍ وسرور", 3, C_BROWN);
        t("عيد سعيد", "أسأل الله أن يتقبل منا ومنكم، وأن يجعل أيامكم كلها أعيادًا", 2, C_WHITE);
        t("تقبل الله منا ومنكم", "عيدكم مبارك، وأعاده الله علينا وعليكم باليُمن والبركات", 0, C_CREAM);

        occ("eid_adha", "عيد الأضحى", "🕋");
        t("عيد أضحى مبارك", "تقبل الله منا ومنكم صالح الأعمال، وكل عام وأنتم بخير", 1, C_GOLD);
        t("كل عام وأنتم بخير", "عساكم من عوّاده، وأعاده الله عليكم بالخير والبركة", 3, C_BROWN);
        t("عيد سعيد", "جعل الله أيامكم أعيادًا وأفراحكم متجددة", 5, C_TEAL);

        occ("arafa", "يوم عرفة", "⛰️");
        t("يوم عرفة", "اللهم اغفر لنا وارحمنا وتقبّل دعاءنا في هذا اليوم العظيم", 3, C_BROWN);
        t("عرفة", "اللهم لا تدع لنا في هذا اليوم ذنبًا إلا غفرته، ولا همًّا إلا فرجته", 0, C_GOLD);
        t("كل عام وأنتم بخير", "تقبل الله حجّاج بيته، وبارك لنا في هذا اليوم المبارك", 11, C_CREAM);

        occ("friday", "الجمعة", "🕌");
        t("جمعة مباركة", "اللهم اجعل لنا في هذا اليوم نورًا وبركةً وفرحًا وقبولًا", 3, C_BROWN);
        t("جمعة طيبة", "اللهم صلِّ وسلِّم وبارك على سيدنا محمد وعلى آله وصحبه", 0, C_GOLD);
        t("يوم الجمعة", "لا تنسَ سورة الكهف، وكثرة الصلاة على النبي ﷺ", 5, C_TEAL);
        t("جمعة مباركة", "اللهم اجعل لنا من كل همٍّ فرجًا ومن كل ضيقٍ مخرجًا", 2, C_CREAM);
        t("جمعة مباركة", "اللهم أصلح لنا ديننا ودنيانا، وبارك لنا في أهلنا وأرزاقنا", 10, C_GREEN);

        occ("mawlid", "المولد النبوي", "🌹");
        t("المولد النبوي الشريف", "اللهم صلِّ وسلِّم وبارك على سيدنا محمد، وكل عام وأنتم بخير", 4, C_GOLD);
        t("صلّوا على النبي", "اللهم صلِّ على محمدٍ وعلى آل محمد، كما صليتَ على إبراهيم وعلى آل إبراهيم، إنك حميدٌ مجيد", 0, C_CREAM);

        occ("ashura", "عاشوراء", "🤲");
        t("يوم عاشوراء", "صيام يوم عاشوراء يُكفّر السنة التي قبله، فلا تنسوا صيامه", 0, C_GOLD);
        t("صوموا عاشوراء", "يُستحب صيام التاسع والعاشر من المحرم", 5, C_TEAL);

        occ("hijri_new_year", "رأس السنة الهجرية", "📅");
        t("عام هجري جديد", "كل عام وأنتم بخير، أعاده الله علينا بالخير واليُمن والبركات", 4, C_GOLD);
        t("كل عام وأنتم بخير", "اللهم أهلّه علينا بالأمن والإيمان والسلامة والإسلام", 9, C_GOLD);

        occ("morning", "أذكار الصباح", "🌅");
        t("صباح الخير", "اللهم بك أصبحنا وبك أمسينا وبك نحيا وبك نموت وإليك النشور", 3, C_BROWN);
        t("صباح النور", "أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له", 2, C_WHITE);
        t("صباح الخير", "اللهم ما أصبح بي من نعمة فمنك وحدك لا شريك لك، فلك الحمد ولك الشكر", 10, C_GREEN);

        occ("evening", "أذكار المساء", "🌆");
        t("مساء الخير", "اللهم بك أمسينا وبك أصبحنا وبك نحيا وبك نموت وإليك المصير", 9, C_GOLD);
        t("مساء النور", "أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له", 11, C_CREAM);
        t("مساء الخير", "اللهم عافني في بدني، اللهم عافني في سمعي، اللهم عافني في بصري", 4, C_WHITE);

        occ("newborn", "مولود جديد", "👶");
        t("مبارك المولود", "بارك الله لك في الموهوب لك، وشكرتَ الواهب، وبلغ أشدّه، ورُزقتَ بِرّه", 6, C_ROSE);
        t("ألف مبروك", "جعله الله قرّة عينٍ لكما وذريّةً صالحة", 7, C_BLUE);

        occ("wedding", "زواج", "💍");
        t("بارك الله لكما", "بارك الله لكما وبارك عليكما وجمع بينكما في خير", 8, C_CREAM);
        t("مبروك الزواج", "اللهم ألّف بين قلبيهما، واجعل بينهما مودةً ورحمةً وسكنًا", 6, C_ROSE);
        t("ألف مبروك", "زواجٌ ميمون وبيتٌ عامرٌ بالسعادة والمودّة", 5, C_TEAL);

        occ("success", "نجاح وتخرج", "🎓");
        t("مبروك النجاح", "ألف مبروك، نسأل الله لك التوفيق والسداد في كل خطوة", 7, C_BLUE);
        t("مبروك التخرج", "بداية جديدة وطريق مليء بالنجاح، وفقك الله وسدّد خطاك", 10, C_GREEN);

        occ("get_well", "سلامتك", "💐");
        t("طهور إن شاء الله", "شفاك الله وعافاك، وجعل مرضك كفارةً ورفعةً لك", 10, C_GREEN);
        t("سلامتك", "اللهم رب الناس، أذهب الباس، اشفِ أنت الشافي، لا شفاء إلا شفاؤك", 5, C_TEAL);

        occ("hajj", "الحج والعمرة", "🕋");
        t("حجًّا مبرورًا", "حجًّا مبرورًا وسعيًا مشكورًا وذنبًا مغفورًا", 3, C_BROWN);
        t("عمرة مقبولة", "تقبّل الله عمرتكم ويسّر لكم أداءها، وردّكم سالمين غانمين", 11, C_CREAM);

        occ("general", "دعاء وشكر", "🤲");
        t("جزاك الله خيرًا", "وبارك فيك، ونفع بك، وجعل ما قدّمت في ميزان حسناتك", 5, C_TEAL);
        t("بارك الله فيك", "جعلك الله مباركًا أينما كنت، وسدّد خطاك", 0, C_GOLD);
        t("حفظكم الله", "حفظكم الله ورعاكم، وأدام عليكم الصحة والسعادة والعافية", 2, C_WHITE);
        t("اللهم آمين", "اللهم اغفر لنا ولوالدينا وللمسلمين أجمعين", 9, C_GOLD);
    }

    public static Occasion findOccasion(String id) {
        for (Occasion o : OCCASIONS) {
            if (o.id.equals(id)) return o;
        }
        return null;
    }

    public static Template findTemplate(String id) {
        if (id == null) return null;
        for (Occasion o : OCCASIONS) {
            for (Template t : o.templates) {
                if (t.id.equals(id)) return t;
            }
        }
        return null;
    }

    public static List<Template> allTemplates() {
        List<Template> all = new ArrayList<Template>();
        for (Occasion o : OCCASIONS) all.addAll(o.templates);
        return all;
    }

    // ------------------------------------------------------------------
    // كارت اليوم
    // ------------------------------------------------------------------

    /** المناسبة الحالية حسب التاريخ الهجري (أو null لو مفيش مناسبة خاصة النهارده). */
    private static String seasonalOccasion(Context ctx) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            int hijriOffset = 0;
            try { hijriOffset = Integer.parseInt(prefs.getString("hijri_offset", "0")); } catch (Exception ignored) { }
            net.time4j.calendar.HijriCalendar hijriToday =
                    net.time4j.SystemClock.inLocalView().today().transform(
                            net.time4j.calendar.HijriCalendar.class,
                            net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA);
            if (hijriOffset != 0) {
                hijriToday = hijriToday.plus(hijriOffset, net.time4j.calendar.HijriCalendar.Unit.DAYS);
            }
            int day = hijriToday.getDayOfMonth();
            int month = hijriToday.getMonth().getValue() - 1;   // 0-11
            if (month == 8) return "ramadan";
            if (month == 9 && day <= 3) return "eid_fitr";
            if (month == 11 && day == 9) return "arafa";
            if (month == 11 && day >= 10 && day <= 13) return "eid_adha";
            if (month == 0 && day == 1) return "hijri_new_year";
            if (month == 0 && (day == 9 || day == 10)) return "ashura";
            if (month == 2 && day == 12) return "mawlid";
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** قالب كارت اليوم: بيتغير كل يوم، وبيراعي رمضان والأعياد والجمعة. */
    public static Template templateOfDay(Context ctx) {
        long now = System.currentTimeMillis();
        long dayNumber = (now + TimeZone.getDefault().getOffset(now)) / 86400000L;

        List<Template> pool = new ArrayList<Template>();
        String season = seasonalOccasion(ctx);
        if (season != null) {
            Occasion o = findOccasion(season);
            if (o != null) pool.addAll(o.templates);
        }
        if (pool.isEmpty() && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            Occasion o = findOccasion("friday");
            if (o != null) pool.addAll(o.templates);
        }
        if (pool.isEmpty()) {
            String[] evergreen = {"morning", "evening", "general"};
            for (String id : evergreen) {
                Occasion o = findOccasion(id);
                if (o != null) pool.addAll(o.templates);
            }
        }
        return pool.get((int) (dayNumber % pool.size()));
    }
}

package com.alaaeltaweel.thikrallah.cards;

/** حالة الكارت الحالية اللي بيتعدّل عليها المستخدم. */
public final class CardState {
    public String headline = "";
    public String body = "";
    public String details = "";
    public String sender = "";
    public int styleIndex = 0;
    public int paletteIndex = 0;
    public int fontIndex = 0;
    public int textColor = 0xFFFFFFFF;
    public float sizeScale = 1f;
    public boolean portrait = false;

    public CardState copy() {
        CardState s = new CardState();
        s.headline = headline;
        s.body = body;
        s.details = details;
        s.sender = sender;
        s.styleIndex = styleIndex;
        s.paletteIndex = paletteIndex;
        s.fontIndex = fontIndex;
        s.textColor = textColor;
        s.sizeScale = sizeScale;
        s.portrait = portrait;
        return s;
    }

    public static CardState fromTemplate(CardCatalog.Template t, String sender) {
        CardState s = new CardState();
        s.headline = t.headline;
        s.body = t.body;
        s.details = t.details;
        s.styleIndex = t.styleIndex;
        s.paletteIndex = t.paletteIndex;
        s.textColor = t.textColor;
        s.sender = sender == null ? "" : sender;
        return s;
    }

    /** مفتاح فريد لمحتوى الكارت (بيستخدم لتخزين المعاينات المؤقتة). */
    public String signature() {
        return headline + "\u0001" + body + "\u0001" + details + "\u0001" + sender + "\u0001"
                + styleIndex + "\u0001" + paletteIndex + "\u0001" + fontIndex + "\u0001"
                + textColor + "\u0001" + sizeScale + "\u0001" + portrait;
    }
}

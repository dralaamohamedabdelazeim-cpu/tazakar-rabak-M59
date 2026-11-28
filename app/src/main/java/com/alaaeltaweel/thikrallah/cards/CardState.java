package com.alaaeltaweel.thikrallah.cards;

/** حالة الكارت الحالية اللي بيتعدّل عليها المستخدم (النصوص، الخلفية، الخط، اللون، الحجم). */
public final class CardState {
    public String headline = "";
    public String body = "";
    public String sender = "";
    public int backgroundIndex = 0;
    public int fontIndex = 0;
    public int textColor = 0xFFFFFFFF;
    public float sizeScale = 1f;
    public boolean portrait = false;

    public CardState copy() {
        CardState s = new CardState();
        s.headline = headline;
        s.body = body;
        s.sender = sender;
        s.backgroundIndex = backgroundIndex;
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
        s.backgroundIndex = t.backgroundIndex;
        s.textColor = t.textColor;
        s.sender = sender == null ? "" : sender;
        return s;
    }
}

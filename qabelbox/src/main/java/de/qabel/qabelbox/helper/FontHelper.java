package de.qabel.qabelbox.helper;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import de.qabel.qabelbox.R;
import de.qabel.qabelbox.ui.views.TextViewQabelFont;

public class FontHelper {

    public static boolean disable = false;

    private Typeface[] fonts;
    private static FontHelper instance = null;
    private Typeface qabelFont;

    private FontHelper() {
        // Exists only to defeat instantiation.
    }

    public static FontHelper getInstance() {

        if (instance == null) {
            instance = new FontHelper();
        }
        return instance;
    }

    private void loadCustomFonts(Context context) {
        //load fonts in fixed order, 0=normal, 1=bold, 2=italic, 3=bolditalic
        String[] fontList = context.getResources().getStringArray(R.array.fonts);
        fonts = new Typeface[fontList.length];
        qabelFont = Typeface.createFromAsset(context.getAssets(), context.getResources().getString(R.string.asset_qabel_font));

        for (int i = 0; i < fontList.length; i++) {
            fonts[i] = Typeface.createFromAsset(context.getAssets(), fontList[i]);
        }
    }

    public void setCustomFonts(TextView view) {
        if (disable) {
            return;
        }

        if (view != null && !view.isInEditMode()) {
            if (fonts == null) {
                loadCustomFonts(view.getContext());
            }
            int style = Typeface.NORMAL;
            if (view.getTypeface() != null) {
                style = view.getTypeface().getStyle();
            }
            if (style >= 0 && style < fonts.length) {
                view.setTypeface(fonts[style], style);
            }
        }
    }

    public void setQabelFont(TextViewQabelFont tv) {
        if (disable) {
            return;
        }
        if (qabelFont == null) {
            loadCustomFonts(tv.getContext());
        }
        tv.setTypeface(qabelFont);
    }
}

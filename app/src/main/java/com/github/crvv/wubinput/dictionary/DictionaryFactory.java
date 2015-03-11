package com.github.crvv.wubinput.dictionary;

import android.content.Context;
import android.util.Log;

import java.util.Locale;

public class DictionaryFactory {
    private static final String TAG = DictionaryFactory.class.getSimpleName();

    public static Dictionary createDictionary(Context context, Locale locale){
        Log.d(TAG, locale.getDisplayName());
        if(locale.equals(Locale.SIMPLIFIED_CHINESE)) {
            return new WubiDictionary("dictionary type", context);
        }
        return null;
    }
}

package com.android.inputmethod.dictionary;


import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;

public class WubiDictionary extends Dictionary{

    public WubiDictionary(final String type) {
        super(type);
    }

    @Override
    public ArrayList<SuggestedWords.SuggestedWordInfo> getSuggestions(final WordComposer composer,
        final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
        final SettingsValuesForSuggestion settingsValuesForSuggestion,
        final int sessionId, final float[] inOutLanguageWeight) {
        return null;
    }

    @Override
    public boolean isInDictionary(String word) {
        return false;
    }

}

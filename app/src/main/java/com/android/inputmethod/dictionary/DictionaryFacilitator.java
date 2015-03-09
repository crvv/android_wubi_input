/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.dictionary;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.keyboard.ProximityInfo;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.PrevWordsInfo;
import com.android.inputmethod.latin.PrevWordsInfo.WordInfo;
import com.android.inputmethod.dictionary.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.WordComposer;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.DistracterFilter;
import com.android.inputmethod.latin.utils.DistracterFilterCheckingIsInDictionary;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

// TODO: Consolidate dictionaries in native code.
public class DictionaryFacilitator {
    public static final String TAG = DictionaryFacilitator.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;

    private Dictionaries mDictionaries = new Dictionaries();
    private boolean mIsUserDictEnabled = false;
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionary = new CountDownLatch(0);
    // To synchronize assigning mDictionaries to ensure closing dictionaries.
    private final Object mLock = new Object();
    private final DistracterFilter mDistracterFilter;

    private static final String[] DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS =
            new String[] {
                Dictionary.TYPE_MAIN,
                Dictionary.TYPE_USER_HISTORY,
                Dictionary.TYPE_PERSONALIZATION,
                Dictionary.TYPE_USER,
                Dictionary.TYPE_CONTACTS,
                Dictionary.TYPE_CONTEXTUAL
            };

    public static final Map<String, Class<? extends WubiDictionary>> DICT_TYPE_TO_CLASS = new HashMap<>();
//
//    static {
//        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
//        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_PERSONALIZATION, PersonalizationDictionary.class);
//        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
//        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTACTS, ContactsBinaryDictionary.class);
//        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTEXTUAL, ContextualDictionary.class);
//    }

    private static final String DICT_FACTORY_METHOD_NAME = "getDictionary";
    private static final Class<?>[] DICT_FACTORY_METHOD_ARG_TYPES =
            new Class[] { Context.class, Locale.class, File.class, String.class };

    private static final String[] SUB_DICT_TYPES =
            Arrays.copyOfRange(DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS, 1 /* start */,
                    DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS.length);

    /**
     * Class contains dictionaries for a locale.
     */
    private static class Dictionaries {
        public final Locale mLocale;
        private Dictionary mMainDict;
        public final ConcurrentHashMap<String, WubiDictionary> mSubDictMap =
                new ConcurrentHashMap<>();

        public Dictionaries() {
            mLocale = null;
        }

        public Dictionaries(final Locale locale, final Dictionary mainDict,
                final Map<String, WubiDictionary> subDicts) {
            mLocale = locale;
            // Main dictionary can be asynchronously loaded.
            setMainDict(mainDict);
            for (final Map.Entry<String, WubiDictionary> entry : subDicts.entrySet()) {
                setSubDict(entry.getKey(), entry.getValue());
            }
        }

        private void setSubDict(final String dictType, final WubiDictionary dict) {
            if (dict != null) {
                mSubDictMap.put(dictType, dict);
            }
        }

        public void setMainDict(final Dictionary mainDict) {
            // Close old dictionary if exists. Main dictionary can be assigned multiple times.
            final Dictionary oldDict = mMainDict;
            mMainDict = mainDict;
            if (oldDict != null && mainDict != oldDict) {
                oldDict.close();
            }
        }

        public Dictionary getDict(final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict;
            } else {
                return getSubDict(dictType);
            }
        }

        public WubiDictionary getSubDict(final String dictType) {
            return mSubDictMap.get(dictType);
        }

        public boolean hasDict(final String dictType) {
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                return mMainDict != null;
            } else {
                return mSubDictMap.containsKey(dictType);
            }
        }

        public void closeDict(final String dictType) {
            final Dictionary dict;
            if (Dictionary.TYPE_MAIN.equals(dictType)) {
                dict = mMainDict;
            } else {
                dict = mSubDictMap.remove(dictType);
            }
            if (dict != null) {
                dict.close();
            }
        }
    }

    public interface DictionaryInitializationListener {
        public void onUpdateMainDictionaryAvailability(boolean isMainDictionaryAvailable);
    }

    public DictionaryFacilitator() {
        mDistracterFilter = DistracterFilter.EMPTY_DISTRACTER_FILTER;
    }

//    public DictionaryFacilitator(final DistracterFilter distracterFilter) {
//        mDistracterFilter = distracterFilter;
//    }

    public void updateEnabledSubtypes(final List<InputMethodSubtype> enabledSubtypes) {
        mDistracterFilter.updateEnabledSubtypes(enabledSubtypes);
    }

    public Locale getLocale() {
        return mDictionaries.mLocale;
    }

    private static WubiDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix) {
        final Class<? extends WubiDictionary> dictClass =
                DICT_TYPE_TO_CLASS.get(dictType);
        if (dictClass == null) {
            return null;
        }
        try {
            final Method factoryMethod = dictClass.getMethod(DICT_FACTORY_METHOD_NAME,
                    DICT_FACTORY_METHOD_ARG_TYPES);
            final Object dict = factoryMethod.invoke(null /* obj */,
                    new Object[] { context, locale, dictFile, dictNamePrefix });
            return (WubiDictionary) dict;
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "Cannot create dictionary: " + dictType, e);
            return null;
        }
    }

    public void resetDictionaries(final Context context, final Locale newLocale,
            final boolean useContactsDict, final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            final DictionaryInitializationListener listener) {
        resetDictionariesWithDictNamePrefix(context, newLocale, useContactsDict,
                usePersonalizedDicts, forceReloadMainDictionary, listener, "" /* dictNamePrefix */);
    }

    public void resetDictionariesWithDictNamePrefix(final Context context, final Locale newLocale,
            final boolean useContactsDict, final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            final DictionaryInitializationListener listener,
            final String dictNamePrefix) {
        final boolean localeHasBeenChanged = !newLocale.equals(mDictionaries.mLocale);
        // We always try to have the main dictionary. Other dictionaries can be unused.
        final boolean reloadMainDictionary = localeHasBeenChanged || forceReloadMainDictionary;
        // TODO: Make subDictTypesToUse configurable by resource or a static final list.
        final HashSet<String> subDictTypesToUse = new HashSet<>();
        if (useContactsDict) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        subDictTypesToUse.add(Dictionary.TYPE_USER);
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
            subDictTypesToUse.add(Dictionary.TYPE_PERSONALIZATION);
            subDictTypesToUse.add(Dictionary.TYPE_CONTEXTUAL);
        }

        final Dictionary newMainDict;
        if (reloadMainDictionary) {
            // The main dictionary will be asynchronously loaded.
            newMainDict = null;
        } else {
            newMainDict = mDictionaries.getDict(Dictionary.TYPE_MAIN);
        }

        final Map<String, WubiDictionary> subDicts = new HashMap<>();
        for (final String dictType : SUB_DICT_TYPES) {
            if (!subDictTypesToUse.contains(dictType)) {
                // This dictionary will not be used.
                continue;
            }
            final WubiDictionary dict;
            if (!localeHasBeenChanged && mDictionaries.hasDict(dictType)) {
                // Continue to use current dictionary.
                dict = mDictionaries.getSubDict(dictType);
            } else {
                // Start to use new dictionary.
                dict = getSubDict(dictType, context, newLocale, null /* dictFile */,
                        dictNamePrefix);
            }
            subDicts.put(dictType, dict);
        }

        // Replace Dictionaries.
        final Dictionaries newDictionaries = new Dictionaries(newLocale, newMainDict, subDicts);
        final Dictionaries oldDictionaries;
        synchronized (mLock) {
            oldDictionaries = mDictionaries;
            mDictionaries = newDictionaries;
            mIsUserDictEnabled = false;
            if (reloadMainDictionary) {
                asyncReloadMainDictionary(context, newLocale, listener);
            }
        }
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasInitializedMainDictionary());
        }
        // Clean up old dictionaries.
        if (reloadMainDictionary) {
            oldDictionaries.closeDict(Dictionary.TYPE_MAIN);
        }
        for (final String dictType : SUB_DICT_TYPES) {
            if (localeHasBeenChanged || !subDictTypesToUse.contains(dictType)) {
                oldDictionaries.closeDict(dictType);
            }
        }
        oldDictionaries.mSubDictMap.clear();
    }

    private void asyncReloadMainDictionary(final Context context, final Locale locale,
            final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionary = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getExecutor("InitializeBinaryDictionary").execute(new Runnable() {
            @Override
            public void run() {
                final Dictionary mainDict =
                        DictionaryFactory.createDictionary(context, locale);
                synchronized (mLock) {
                    if (locale.equals(mDictionaries.mLocale)) {
                        mDictionaries.setMainDict(mainDict);
                    } else {
                        // Dictionary facilitator has been reset for another locale.
                        mainDict.close();
                    }
                }
                if (listener != null) {
                    listener.onUpdateMainDictionaryAvailability(hasInitializedMainDictionary());
                }
                latchForWaitingLoadingMainDictionary.countDown();
            }
        });
    }


    public void closeDictionaries() {
        final Dictionaries dictionaries;
        synchronized (mLock) {
            dictionaries = mDictionaries;
            mDictionaries = new Dictionaries();
        }
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
            dictionaries.closeDict(dictType);
        }
        mDistracterFilter.close();
    }

    // The main dictionary could have been loaded asynchronously.  Don't cache the return value
    // of this method.
    public boolean hasInitializedMainDictionary() {
        final Dictionary mainDict = mDictionaries.getDict(Dictionary.TYPE_MAIN);
        return mainDict != null && mainDict.isInitialized();
    }

    public boolean isUserDictionaryEnabled() {
        return mIsUserDictEnabled;
    }

    private void removeWord(final String dictName, final String word) {
        final WubiDictionary dictionary = mDictionaries.getSubDict(dictName);
        if (dictionary != null) {
//            dictionary.removeUnigramEntryDynamically(word);
        }
    }

    public void removeWordFromPersonalizedDicts(final String word) {
        removeWord(Dictionary.TYPE_USER_HISTORY, word);
        removeWord(Dictionary.TYPE_PERSONALIZATION, word);
        removeWord(Dictionary.TYPE_CONTEXTUAL, word);
    }

    // TODO: Revise the way to fusion suggestion results.
    public SuggestionResults getSuggestionResults(final WordComposer composer,
            final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
            final SettingsValuesForSuggestion settingsValuesForSuggestion, final int sessionId) {
        final Dictionaries dictionaries = mDictionaries;
        final SuggestionResults suggestionResults = new SuggestionResults(
                dictionaries.mLocale, SuggestedWords.MAX_SUGGESTIONS,
                prevWordsInfo.mPrevWordsInfo[0].mIsBeginningOfSentence);
        final float[] languageWeight = new float[] { Dictionary.NOT_A_LANGUAGE_WEIGHT };
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
            final Dictionary dictionary = dictionaries.getDict(dictType);
            if (null == dictionary) continue;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestions(composer, prevWordsInfo, proximityInfo,
                            settingsValuesForSuggestion, sessionId, languageWeight);
            if (null == dictionarySuggestions) continue;
            suggestionResults.addAll(dictionarySuggestions);
            if (null != suggestionResults.mRawSuggestions) {
                suggestionResults.mRawSuggestions.addAll(dictionarySuggestions);
            }
        }
        return suggestionResults;
    }

    public boolean isValidWord(final String word, final boolean ignoreCase) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        final Dictionaries dictionaries = mDictionaries;
        if (dictionaries.mLocale == null) {
            return false;
        }
        final String lowerCasedWord = word.toLowerCase(dictionaries.mLocale);
        for (final String dictType : DICT_TYPES_ORDERED_TO_GET_SUGGESTIONS) {
            final Dictionary dictionary = dictionaries.getDict(dictType);
            // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
            // would be immutable once it's finished initializing, but concretely a null test is
            // probably good enough for the time being.
            if (null == dictionary) continue;
            if (dictionary.isValidWord(word)
                    || (ignoreCase && dictionary.isValidWord(lowerCasedWord))) {
                return true;
            }
        }
        return false;
    }
}

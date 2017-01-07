/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.github.crvv.wubinput.wubi.settings;

import android.content.res.Resources;

import com.github.crvv.wubinput.wubi.Constants;
import com.github.crvv.wubinput.wubi.R;
import com.github.crvv.wubinput.wubi.utils.StringUtils;

import java.util.Arrays;
import java.util.Locale;

public final class SpacingAndPunctuations {
    private final int[] mSortedSymbolsPrecededBySpace;
    private final int[] mSortedSymbolsFollowedBySpace;
    private final int[] mSortedSymbolsClusteringTogether;
    private final int[] mSortedWordConnectors;
    public final int[] mSortedWordSeparators;
    private final int mSentenceSeparator;
    public final String mSentenceSeparatorAndSpace;
    public final boolean mCurrentLanguageHasSpaces;
    public final boolean mUsesAmericanTypography;
    public final boolean mUsesGermanRules;

    public SpacingAndPunctuations(final Resources res) {
        // To be able to binary search the code point. See {@link #isUsuallyPrecededBySpace(int)}.
        mSortedSymbolsPrecededBySpace = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_preceded_by_space));
        // To be able to binary search the code point. See {@link #isUsuallyFollowedBySpace(int)}.
        mSortedSymbolsFollowedBySpace = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_followed_by_space));
        mSortedSymbolsClusteringTogether = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_clustering_together));
        // To be able to binary search the code point. See {@link #isWordConnector(int)}.
        mSortedWordConnectors = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_word_connectors));
        mSortedWordSeparators = StringUtils.toSortedCodePointArray(
                res.getString(R.string.symbols_word_separators));
        mSentenceSeparator = res.getInteger(R.integer.sentence_separator);
        mSentenceSeparatorAndSpace = new String(new int[] {
                mSentenceSeparator, Constants.CODE_SPACE }, 0, 2);
        mCurrentLanguageHasSpaces = false;
        final Locale locale = res.getConfiguration().locale;
        // Heuristic: we use American Typography rules because it's the most common rules for all
        // English variants. German rules (not "German typography") also have small gotchas.
        mUsesAmericanTypography = Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
        mUsesGermanRules = Locale.GERMAN.getLanguage().equals(locale.getLanguage());
    }

    public boolean isWordSeparator(final int code) {
        return Arrays.binarySearch(mSortedWordSeparators, code) >= 0;
    }

    public boolean isWordConnector(final int code) {
        return Arrays.binarySearch(mSortedWordConnectors, code) >= 0;
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsPrecededBySpace, code) >= 0;
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return Arrays.binarySearch(mSortedSymbolsFollowedBySpace, code) >= 0;
    }

    public boolean isClusteringSymbol(final int code) {
        return Arrays.binarySearch(mSortedSymbolsClusteringTogether, code) >= 0;
    }

    public boolean isSentenceSeparator(final int code) {
        return code == mSentenceSeparator;
    }
}

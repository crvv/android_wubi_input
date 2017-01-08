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

package com.github.crvv.wubinput.wubi.dictionary.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.crvv.wubinput.accessibility.AccessibilityUtils;
import com.github.crvv.wubinput.annotations.UsedForTesting;
import com.github.crvv.wubinput.wubi.R;
import com.github.crvv.wubinput.wubi.dictionary.SuggestedWords;
import com.github.crvv.wubinput.wubi.dictionary.SuggestedWords.SuggestedWordInfo;
import com.github.crvv.wubinput.wubi.settings.Settings;
import com.github.crvv.wubinput.wubi.settings.SettingsValues;
import com.github.crvv.wubinput.wubi.utils.ResourceUtils;
import com.github.crvv.wubinput.wubi.utils.ViewLayoutUtils;

import java.util.ArrayList;

final class SuggestionStripLayoutHelper {
    public static final String TAG = SuggestionStripLayoutHelper.class.getSimpleName();
    private static final int DEFAULT_MAX_MORE_SUGGESTIONS_ROW = 2;
    private static final float MIN_TEXT_XSCALE = 0.70f;

    public final int mPadding;
    public final int mDividerWidth;
    public final int mSuggestionsStripHeight;
    public final int mMoreSuggestionsRowHeight;
    private int mMaxMoreSuggestionsRow;
    public final float mMinMoreSuggestionsWidth;
    public final int mMoreSuggestionsBottomGap;

    // The index of these {@link ArrayList} is the position in the suggestion strip. The indices
    // increase towards the right for LTR scripts and the left for RTL scripts, starting with 0.
    // The position of the most important suggestion is in {@link #mCenterPositionInStrip}
    private final ArrayList<TextView> mWordViews;
    private final ArrayList<View> mDividerViews;

    private final int mColorValidTypedWord;
    private final int mColorTypedWord;
    private final int mColorAutoCorrect;
    private final int mColorSuggested;
    private final float mAlphaObsoleted;
    private static final String MORE_SUGGESTIONS_HINT = "\u2024";
    private static final String LEFTWARDS_ARROW = "\u2190";
    private static final String RIGHTWARDS_ARROW = "\u2192";

    private static final CharacterStyle BOLD_SPAN = new StyleSpan(Typeface.BOLD);
    private static final CharacterStyle UNDERLINE_SPAN = new UnderlineSpan();

    private final int mSuggestionStripOptions;
    // These constants are the flag values of
    // {@link R.styleable#SuggestionStripView_suggestionStripOptions} attribute.
    private static final int AUTO_CORRECT_BOLD = 0x01;
    private static final int AUTO_CORRECT_UNDERLINE = 0x02;
    private static final int VALID_TYPED_WORD_BOLD = 0x04;

    public SuggestionStripLayoutHelper(final Context context, final AttributeSet attrs,
            final int defStyle, final ArrayList<TextView> wordViews,
            final ArrayList<View> dividerViews, final ArrayList<TextView> debugInfoViews) {
        mWordViews = wordViews;
        mDividerViews = dividerViews;

        final TextView wordView = wordViews.get(0);
        final View dividerView = dividerViews.get(0);
        mPadding = wordView.getCompoundPaddingLeft() + wordView.getCompoundPaddingRight();
        dividerView.measure(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mDividerWidth = dividerView.getMeasuredWidth();

        final Resources res = wordView.getResources();
        mSuggestionsStripHeight = res.getDimensionPixelSize(
                R.dimen.config_suggestions_strip_height);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SuggestionStripView, defStyle, R.style.SuggestionStripView);
        mSuggestionStripOptions = a.getInt(
                R.styleable.SuggestionStripView_suggestionStripOptions, 0);
        mAlphaObsoleted = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_alphaObsoleted, 1.0f);
        mColorValidTypedWord = a.getColor(R.styleable.SuggestionStripView_colorValidTypedWord, 0);
        mColorTypedWord = a.getColor(R.styleable.SuggestionStripView_colorTypedWord, 0);
        mColorAutoCorrect = a.getColor(R.styleable.SuggestionStripView_colorAutoCorrect, 0);
        mColorSuggested = a.getColor(R.styleable.SuggestionStripView_colorSuggested, 0);
        mMaxMoreSuggestionsRow = a.getInt(
                R.styleable.SuggestionStripView_maxMoreSuggestionsRow,
                DEFAULT_MAX_MORE_SUGGESTIONS_ROW);
        mMinMoreSuggestionsWidth = ResourceUtils.getFraction(a,
                R.styleable.SuggestionStripView_minMoreSuggestionsWidth, 1.0f);
        a.recycle();

        // Assuming there are at least three suggestions. Also, note that the suggestions are
        // laid out according to script direction, so this is left of the center for LTR scripts
        // and right of the center for RTL scripts.
        mMoreSuggestionsBottomGap = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_bottom_gap);
        mMoreSuggestionsRowHeight = res.getDimensionPixelSize(
                R.dimen.config_more_suggestions_row_height);
    }

    public int getMaxMoreSuggestionsRow() {
        return mMaxMoreSuggestionsRow;
    }

    private int getMoreSuggestionsHeight() {
        return mMaxMoreSuggestionsRow * mMoreSuggestionsRowHeight + mMoreSuggestionsBottomGap;
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        final int currentHeight = getMoreSuggestionsHeight();
        if (currentHeight <= remainingHeight) {
            return;
        }

        mMaxMoreSuggestionsRow = (remainingHeight - mMoreSuggestionsBottomGap)
                / mMoreSuggestionsRowHeight;
    }

    private static Drawable getMoreSuggestionsHint(final Resources res, final float textSize, final int color) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(textSize);
        paint.setColor(color);
        final Rect bounds = new Rect();
        paint.getTextBounds(MORE_SUGGESTIONS_HINT, 0, MORE_SUGGESTIONS_HINT.length(), bounds);
        final int width = Math.round(bounds.width() + 0.5f);
        final int height = Math.round(bounds.height() + 0.5f);
        final Bitmap buffer = Bitmap.createBitmap(width, (height * 3 / 2), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawText(MORE_SUGGESTIONS_HINT, width / 2, height, paint);
        return new BitmapDrawable(res, buffer);
    }

    private CharSequence getStyledSuggestedWord(final SuggestedWords suggestedWords, final int indexInSuggestedWords) {
        if (indexInSuggestedWords >= suggestedWords.size()) {
            return null;
        }
        final String word = suggestedWords.getLabel(indexInSuggestedWords);
        // TODO: don't use the index to decide whether this is the auto-correction/typed word, as
        // this is brittle
        final boolean isAutoCorrection = suggestedWords.mWillAutoCorrect
                && indexInSuggestedWords == SuggestedWords.INDEX_OF_AUTO_CORRECTION;
        final boolean isTypedWordValid = suggestedWords.mTypedWordValid
                && indexInSuggestedWords == SuggestedWords.INDEX_OF_TYPED_WORD;
        if (!isAutoCorrection && !isTypedWordValid) {
            return word;
        }

        final int len = word.length();
        final Spannable spannedWord = new SpannableString(word);
        final int options = mSuggestionStripOptions;
        if ((isAutoCorrection && (options & AUTO_CORRECT_BOLD) != 0)
                || (isTypedWordValid && (options & VALID_TYPED_WORD_BOLD) != 0)) {
            spannedWord.setSpan(BOLD_SPAN, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        if (isAutoCorrection && (options & AUTO_CORRECT_UNDERLINE) != 0) {
            spannedWord.setSpan(UNDERLINE_SPAN, 0, len, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return spannedWord;
    }

    private int getSuggestionTextColor(final SuggestedWords suggestedWords,
            final int indexInSuggestedWords) {
        // Use identity for strings, not #equals : it's the typed word if it's the same object
        final boolean isTypedWord = suggestedWords.getInfo(indexInSuggestedWords).isKindOf(
                SuggestedWordInfo.KIND_TYPED);

        final int color;
        if (indexInSuggestedWords == SuggestedWords.INDEX_OF_AUTO_CORRECTION
                && suggestedWords.mWillAutoCorrect) {
            color = mColorAutoCorrect;
        } else if (isTypedWord && suggestedWords.mTypedWordValid) {
            color = mColorValidTypedWord;
        } else if (isTypedWord) {
            color = mColorTypedWord;
        } else {
            color = mColorSuggested;
        }

        if (suggestedWords.mIsObsoleteSuggestions && !isTypedWord) {
            return applyAlpha(color, mAlphaObsoleted);
        }
        return color;
    }

    private static int applyAlpha(final int color, final float alpha) {
        final int newAlpha = (int)(Color.alpha(color) * alpha);
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void addDivider(final ViewGroup stripView, final View dividerView) {
        stripView.addView(dividerView);
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)dividerView.getLayoutParams();
        params.gravity = Gravity.CENTER;
    }

    /**
     * Layout suggestions to the suggestions strip. And returns the start index of more
     * suggestions.
     *
     * @param suggestedWords suggestions to be shown in the suggestions strip.
     * @param stripView the suggestions strip view.
     * @param placerView the view where the debug info will be placed.
     * @return the start index of more suggestions.
     */
    public void layoutSuggestions(final SuggestedWords suggestedWords,
            final ViewGroup stripView, final ViewGroup placerView) {
        setupWordViews(suggestedWords);
        final int stripWidth = stripView.getWidth();

        int x = 0, i = 0;
        for (TextView wordView: mWordViews) {
            if (i != 0) {
                final View divider = mDividerViews.get(i);
                // Add divider if this isn't the left most suggestion in suggestions strip.
                addDivider(stripView, divider);
                x += divider.getMeasuredWidth();
            }
            stripView.addView(wordView);
            setLayoutWeight(wordView, 0, ViewGroup.LayoutParams.MATCH_PARENT);
            x += wordView.getMeasuredWidth();
            if (x > stripWidth) {
                break;
            }
            i++;
        }
    }

    private void setupWordViews(final SuggestedWords suggestedWords) {
        // Clear all suggestions first
        for (TextView wordView : mWordViews) {
            wordView.setText(null);
            wordView.setTag(null);
            wordView.setEnabled(false);
        }
        for (int wordIndex = 1; wordIndex < suggestedWords.size(); wordIndex++) {
            int viewIndex = wordIndex - 1;
            CharSequence word = suggestedWords.getWord(wordIndex);
            final TextView wordView = mWordViews.get(viewIndex);
            wordView.setTag(wordIndex);
            wordView.setText(word);
            wordView.setTextColor(getSuggestionTextColor(suggestedWords, wordIndex));
            wordView.setEnabled(!TextUtils.isEmpty(word));
        }
    }

    private static void setLayoutWeight(final View v, final float weight, final int height) {
        final ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            final LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams)lp;
            llp.weight = weight;
            llp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            llp.height = height;
        }
    }

    private static float getTextScaleX(final CharSequence text, final int maxWidth, final TextPaint paint) {
        paint.setTextScaleX(1.0f);
        final int width = getTextWidth(text, paint);
        if (width <= maxWidth || maxWidth <= 0) {
            return 1.0f;
        }
        return maxWidth / (float)width;
    }

    private static int getTextWidth(final CharSequence text, final TextPaint paint) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        final Typeface savedTypeface = paint.getTypeface();
        paint.setTypeface(getTextTypeface(text));
        final int len = text.length();
        final float[] widths = new float[len];
        final int count = paint.getTextWidths(text, 0, len, widths);
        int width = 0;
        for (int i = 0; i < count; i++) {
            width += Math.round(widths[i] + 0.5f);
        }
        paint.setTypeface(savedTypeface);
        return width;
    }

    private static Typeface getTextTypeface(final CharSequence text) {
        if (!(text instanceof SpannableString)) {
            return Typeface.DEFAULT;
        }

        final SpannableString ss = (SpannableString)text;
        final StyleSpan[] styles = ss.getSpans(0, text.length(), StyleSpan.class);
        if (styles.length == 0) {
            return Typeface.DEFAULT;
        }

        if (styles[0].getStyle() == Typeface.BOLD) {
            return Typeface.DEFAULT_BOLD;
        }
        // TODO: BOLD_ITALIC, ITALIC case?
        return Typeface.DEFAULT;
    }
}

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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.Preference;

import com.github.crvv.wubinput.wubi.AudioAndHapticFeedbackManager;
import com.github.crvv.wubinput.wubi.R;
import com.github.crvv.wubinput.wubi.SubtypeSwitcher;

/**
 * "Preferences" settings sub screen.
 *
 * This settings sub screen handles the following input preferences.
 * - Auto-capitalization
 * - Double-space period
 * - Vibrate on keypress
 * - Sound on keypress
 * - Popup on keypress
 * - Voice input key
 */
public final class PreferencesSettingsFragment extends SubScreenFragment {
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_preferences);

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        SubtypeSwitcher.init(context);

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            removePreference(Settings.PREF_VOICE_INPUT_KEY);
        }
        if (!AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            removePreference(Settings.PREF_VIBRATE_ON);
        }
        if (!Settings.readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            removePreference(Settings.PREF_POPUP_ON);
        }

        refreshEnablingsOfKeypressSoundAndVibrationSettings();
        setupKeypressSoundVolumeSettings();
        setupKeypressVibrationDurationSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        final Preference voiceInputKeyOption = findPreference(Settings.PREF_VOICE_INPUT_KEY);
        if (voiceInputKeyOption != null) {
            final boolean isShortcutImeEnabled = SubtypeSwitcher.getInstance()
                    .isShortcutImeEnabled();
            voiceInputKeyOption.setEnabled(isShortcutImeEnabled);
            voiceInputKeyOption.setSummary(
                    isShortcutImeEnabled ? null : getText(R.string.voice_input_disabled_summary));
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        final Resources res = getResources();
        if (key.equals(Settings.PREF_POPUP_ON)) {
            setPreferenceEnabled(Settings.PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY,
                    Settings.readKeyPreviewPopupEnabled(prefs, res));
        }
        refreshEnablingsOfKeypressSoundAndVibrationSettings();
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        setPreferenceEnabled(Settings.PREF_VIBRATION_DURATION_SETTINGS,
                Settings.readVibrationEnabled(prefs, res));
        setPreferenceEnabled(Settings.PREF_KEYPRESS_SOUND_VOLUME,
                Settings.readKeypressSoundEnabled(prefs, res));
    }

    private void setupKeypressVibrationDurationSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_VIBRATION_DURATION_SETTINGS);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeypressVibrationDuration(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeypressVibrationDuration(res);
            }

            @Override
            public void feedbackValue(final int value) {
                AudioAndHapticFeedbackManager.getInstance().vibrate(value);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }
        });
    }

    private void setupKeypressSoundVolumeSettings() {
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEYPRESS_SOUND_VOLUME);
        if (pref == null) {
            return;
        }
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final AudioManager am = (AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE);
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int) (floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(Settings.readKeypressSoundVolume(prefs, res));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(Settings.readDefaultKeypressSoundVolume(res));
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return Integer.toString(value);
            }

            @Override
            public void feedbackValue(final int value) {
                am.playSoundEffect(
                        AudioManager.FX_KEYPRESS_STANDARD, getValueFromPercentage(value));
            }
        });
    }
}

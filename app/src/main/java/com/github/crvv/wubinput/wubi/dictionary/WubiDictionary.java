package com.github.crvv.wubinput.wubi.dictionary;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import com.github.crvv.wubinput.keyboard.ProximityInfo;
import com.github.crvv.wubinput.wubi.PrevWordsInfo;
import com.github.crvv.wubinput.wubi.R;
import com.github.crvv.wubinput.wubi.WordComposer;
import com.github.crvv.wubinput.wubi.settings.SettingsValuesForSuggestion;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import static com.github.crvv.wubinput.wubi.dictionary.SuggestedWords.MAX_SUGGESTIONS;

public class WubiDictionary extends Dictionary {

    private boolean mIsInitialized = false;
    private static final String TAG = "Dictionary";
    private WubiWordsDbHelper dbHelper;

    public WubiDictionary(final String type, Context context) {
        super(type);
        initDictionary(context);
    }

    @Override
    public ArrayList<SuggestedWords.SuggestedWordInfo>
    getSuggestions(final WordComposer composer,
                   final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
                   final SettingsValuesForSuggestion settingsValuesForSuggestion,
                   final int sessionId, final float[] inOutLanguageWeight) {

        String code = composer.getTypedWord();
        List<String> words = dbHelper.getWords(code);
        if (words == null) words = new ArrayList<>();
        ArrayList<SuggestedWords.SuggestedWordInfo> suggestedWords = new ArrayList<>();
        int index = 0;
        for (String word : words) {
            suggestedWords.add(new SuggestedWords.SuggestedWordInfo(word, 1000 - index, SuggestedWords.SuggestedWordInfo.KIND_CORRECTION, this, index, 1));
            index++;
        }
        return suggestedWords;

    }

    @Override
    public boolean isInDictionary(String word) {
        return true;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public void close() {
        dbHelper.close();
    }

    private void initDictionary(Context context) {
        dbHelper = new WubiWordsDbHelper(context);
        mIsInitialized = true;
    }

    private static class WubiWordsDbHelper extends SQLiteOpenHelper implements BaseColumns {
        private static final String TABLE_NAME = "wubi_words";
        private static final String COLUMN_NAME_CODE = "code";
        private static final String COLUMN_NAME_WORD = "word";
        private static final String SQL_CREATE_ENTRIES =
                "CREATE TABLE " + WubiWordsDbHelper.TABLE_NAME + " (" +
                        WubiWordsDbHelper._ID + " INTEGER PRIMARY KEY," +
                        WubiWordsDbHelper.COLUMN_NAME_CODE + " TEXT," +
                        WubiWordsDbHelper.COLUMN_NAME_WORD + " TEXT)";
        private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + WubiWordsDbHelper.TABLE_NAME;
        private static final String SQL_CREATE_INDEX = "CREATE INDEX wubi_words_code ON wubi_words(code)";
        private static final int DATABASE_VERSION = 1;
        private static final String DATABASE_NAME = "FeedReader.db";
        private SQLiteDatabase db;
        private Context context;

        private WubiWordsDbHelper(Context c) {
            super(c, DATABASE_NAME, null, DATABASE_VERSION);
            context = c;
            db = this.getReadableDatabase();
            db.execSQL("PRAGMA case_sensitive_like = true");
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
            long start = System.currentTimeMillis();

            InputStream is = context.getResources().openRawResource(R.raw.wubi);
            try {
                String insertDataSql = IOUtils.toString(is, StandardCharsets.UTF_8);
                db.execSQL(insertDataSql);
                db.execSQL(SQL_CREATE_INDEX);
                Log.i(TAG, "reading resource file use " + String.valueOf(System.currentTimeMillis() - start) + " ms");
            } catch (IOException e) {
                Log.e(TAG, "read from resource file failed");
            } finally {
                IOUtils.closeQuietly(is);
            }
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is
            // to simply to discard the data and start over
            db.execSQL(SQL_DELETE_ENTRIES);
            onCreate(db);
        }

        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }

        private List<String> getWords(String code) {
            if (code == null || code.length() == 0) {
                return null;
            }
            String[] selectArgs = {code.replace('z', '_') + "%"};
            String sql = MessageFormat.format(
                    "SELECT {2} FROM {0} WHERE {1} LIKE ? ORDER BY {1} ASC LIMIT {3}",
                    TABLE_NAME, COLUMN_NAME_CODE, COLUMN_NAME_WORD, MAX_SUGGESTIONS);
            Cursor cursor = db.rawQuery(sql, selectArgs);
            List<String> result = new ArrayList<>();
            while (cursor.moveToNext()) {
                String word = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME_WORD));
                result.add(word);
            }
            cursor.close();
            return result;
        }
    }
}

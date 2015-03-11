package com.github.crvv.wubinput.dictionary;


import android.content.Context;
import android.util.Log;

import com.github.crvv.wubinput.keyboard.ProximityInfo;
import com.github.crvv.wubinput.wubi.PrevWordsInfo;
import com.github.crvv.wubinput.wubi.R;
import com.github.crvv.wubinput.wubi.WordComposer;
import com.github.crvv.wubinput.wubi.settings.SettingsValuesForSuggestion;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WubiDictionary extends Dictionary{

    private boolean isAvailable = false;
    private static final String LOG_TAG = "Dictionary";
    private Context main;
    private Map<String, ArrayList<String>> mDict;

    private static final int BUFFER_SIZE = 8192;

    public WubiDictionary(final String type, Context context) {
        super(type);
        main = context;
        ReadDictionary();
    }

    @Override
    public ArrayList<SuggestedWords.SuggestedWordInfo> getSuggestions(final WordComposer composer,
        final PrevWordsInfo prevWordsInfo, final ProximityInfo proximityInfo,
        final SettingsValuesForSuggestion settingsValuesForSuggestion,
        final int sessionId, final float[] inOutLanguageWeight) {

        ArrayList<String> words = mDict.get(composer.getTypedWord());
        if(words == null)words = new ArrayList<>();
        ArrayList<SuggestedWords.SuggestedWordInfo> suggestedWords = new ArrayList<>();
        int index = 0;
        for(String word : words){
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
    public boolean isInitialized(){
        return isAvailable;
    }

    private Map<String, ArrayList<String>> readDictFromResource(){
        Map<String, ArrayList<String>> dict = new HashMap<>(262144);
        BufferedReader bufferedResourceReader = null;

        try {
            long start = System.currentTimeMillis();
            InputStream resourceFileInputStream = main.getResources().openRawResource(R.raw.wubi);
            InputStreamReader resourceReader = new InputStreamReader(resourceFileInputStream, "utf-8");
            bufferedResourceReader = new BufferedReader(resourceReader, BUFFER_SIZE);

            String line;
            while ((line = bufferedResourceReader.readLine()) != null) {
                String[] words = line.split(" ");
                if(dict.containsKey(words[0])){
                    for(int i = 1; i < words.length; i++)
                        dict.get(words[0]).add(words[i]);
                }
                else {
                    ArrayList<String> list = new ArrayList<>();
                    for(int i = 1; i < words.length; i++)
                        list.add(words[i]);
                    dict.put(words[0], list);
                }
            }
            Log.i(LOG_TAG, "read from resource file success, " + String.valueOf(System.currentTimeMillis() - start));
        }
        catch(Exception e){
            Log.e(LOG_TAG, "read from resource file failed");
            return null;
        }
        finally{
            try{if(bufferedResourceReader != null)bufferedResourceReader.close();}
            catch(Exception e){}
        }
        return dict;
    }

    private void ReadDictionary() {
        mDict = readDictFromResource();
        isAvailable = true;
    }
}

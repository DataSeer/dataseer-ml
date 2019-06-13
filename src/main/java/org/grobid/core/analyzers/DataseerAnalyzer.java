package org.grobid.core.analyzers;

import org.grobid.core.layout.LayoutToken;
import org.grobid.core.lang.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Tokenizer for all Indo-European languages and identifying dataset mentions.
 *
 * @author Patrice
 */

public class DataseerAnalyzer implements org.grobid.core.analyzers.Analyzer {

    private static volatile DataseerAnalyzer instance;

    public static DataseerAnalyzer getInstance() {
        if (instance == null) {
            //double check idiom
            // synchronized (instanceController) {
                if (instance == null)
                    getNewInstance();
            // }
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new DataseerAnalyzer();
    }

    /**
     * Hidden constructor
     */
    private DataseerAnalyzer() {
    }

    public static final String DELIMITERS = " \n\r\t([^%‰°,:;?.!/)-–−=≈<>+\"“”‘’'`$®]*\u2666\u2665\u2663\u2660\u00A0";
    private static final String REGEX = "(?<=[a-zA-Z])(?=\\d)|(?<=\\d)(?=\\D)";

    public String getName() {
        return "DataseerAnalyzer";
    }

    public List<String> tokenize(String text) {
        // TBD: if we want to support non Indo-European languages, we should make the tokenization
        // language specific
        return tokenize(text, null);
    }

    public List<String> tokenize(String text, Language lang) {
        List<String> result = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(text, DELIMITERS, true);
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            // in addition we split "letter" characters and digits
            String[] subtokens = token.split(REGEX);
            for (int i = 0; i < subtokens.length; i++) {
                result.add(subtokens[i]);
            }
        }
        return result;
    }

    public List<LayoutToken> tokenizeWithLayoutToken(String text) {
        List<LayoutToken> result = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(text, DELIMITERS, true);
        int ind = 0;
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            // in addition we split "letter" characters and digits
            String[] subtokens = token.split(REGEX);
            for (int i = 0; i < subtokens.length; i++) {
                LayoutToken layoutToken = new LayoutToken();
                layoutToken.setText(subtokens[i]);
                layoutToken.setOffset(ind);
                result.add(layoutToken);
                ind += layoutToken.getText().length();
            }
        }

        return result;
    }

    public List<String> retokenize(List<String> chunks) {
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            result.addAll(tokenize(chunk));
        }
        return result;
    }

    public List<LayoutToken> retokenizeLayoutTokens(List<LayoutToken> tokens) {
        List<LayoutToken> result = new ArrayList<>();
        for (LayoutToken token : tokens) {
            result.addAll(tokenize(token));
        }
        return result;
    }

     public List<LayoutToken> tokenize(LayoutToken chunk) {
        List<LayoutToken> result = new ArrayList<>();
        String text = chunk.getText();
        StringTokenizer st = new StringTokenizer(text, DELIMITERS, true);
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            // in addition we split "letter" characters and digits
            String[] subtokens = token.split(REGEX);
            for (int i = 0; i < subtokens.length; i++) {
                LayoutToken theChunk = new LayoutToken(chunk); // deep copy
                theChunk.setText(subtokens[i]);
                result.add(theChunk);
            }
        }

        return result;
    } 
}
package org.grobid.core.lexicon;

import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Class for managing the lexical resources for dataseer
 *
 * @author Patrice
 */
public class DataseerLexicon {

    // Dataset base types
    public enum DataType {
        UNKNOWN("UNKNOWN"),
        GENERIC("GENERIC");

        private String name;

        private DataType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static Logger LOGGER = LoggerFactory.getLogger(DataseerLexicon.class);

    private static volatile DataseerLexicon instance;

    private List<String> englishStopwords = null;

    public static synchronized DataseerLexicon getInstance() {
        if (instance == null)
            instance = new DataseerLexicon();

        return instance;
    }

    private DataseerLexicon() {
        Lexicon.getInstance();
        // init the lexicon
        LOGGER.info("Init dataseer lexicon");

        // a list of stopwords for English for conservative checks with names
        englishStopwords = new ArrayList<>();
        File file = new File("resources/lexicon/stopwords_en.txt");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize English stopwords, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize English stopwords, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        BufferedReader dis = null;
        try {
            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                englishStopwords.add(l.trim());
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("English stopwords file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read English stopwords file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }
    }

    public boolean isEnglishStopword(String value) {
        if (this.englishStopwords == null || value == null)
            return false;
        if (value.length() == 1) 
            value = value.toLowerCase();
        return this.englishStopwords.contains(value);
    }
}

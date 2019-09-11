 package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeaturesVectorDataseer;
import org.grobid.core.features.FeatureFactory;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.DataseerLexicon;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.counters.CntManager;
import org.grobid.core.utilities.counters.impl.CntManagerFactory;
import org.grobid.core.lexicon.FastMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xml.sax.InputSource;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Text;

import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Identification of the article sections introducing datasets.
 *
 * @author Patrice
 */
public class DataseerParser extends AbstractParser {
    private static final Logger logger = LoggerFactory.getLogger(DataseerParser.class);

    // default bins for relative position
    private static final int NBBINS_POSITION = 12;

    private static volatile DataseerParser instance;

    public static DataseerParser getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new DataseerParser();
    }

    private DataseerLexicon dataseerLexicon = null;
    private EngineParsers parsers;

    private DataseerParser() {
        super(GrobidModels.DATASEER, CntManagerFactory.getCntManager(), 
            GrobidCRFEngine.valueOf(DataseerProperties.get("grobid.dataseer.engine").toUpperCase()));

        dataseerLexicon = DataseerLexicon.getInstance();
        parsers = new EngineParsers();
    }

    /**
     * Sequence labelling of a text segments for identifying pieces corresponding to 
     * section introducing data sets (e.g. Materials and Methods section). 
     *
     * @param segments the list of textual segments, segmented into LayoutTokens
     * @param sectionTypes list giving for each segment its section type as String (head, paragraph, list)
     * @param hasDatasets list giving for each segment if the classifier has predicted a dataset (has_dataset) as boolean
     * @param datasetTypes list giving for each segment the classifier prediction as data type as String, or null of has_dataset
     * 
     * @return list of Boolean, one for each inputed text segment, indicating if the segment
     * is relevant for data set section. 
     */
    public List<Boolean> processing(List<List<LayoutToken>> segments, List<String> sectionTypes, List<Boolean> hasDatasets, List<String> datasetTypes) {
        String content = getFeatureVectorsAsString(segments, sectionTypes, hasDatasets, datasetTypes);
        List<Boolean> result = new ArrayList<Boolean>();
        if (isNotEmpty(trim(content))) {
            String labelledResult = label(content);
            // set the boolean value for the segments

        }

        return result;
    }

    public List<Boolean> processingText(List<String> segments, List<String> sectionTypes, List<Boolean> hasDatasets, List<String> datasetTypes) {
        List<List<LayoutToken>> layoutTokenSegments = new ArrayList<List<LayoutToken>>();
        for(String segment : segments) {
            List<LayoutToken> tokens = DataseerAnalyzer.getInstance().tokenizeWithLayoutToken(segment);
            layoutTokenSegments.add(tokens);
        }
        return processing(layoutTokenSegments, sectionTypes, hasDatasets, datasetTypes);
    }

    /**
     * Addition of the features at segment level for the complete set of segments.
     * <p/>
     * This is an alternative to the token level, where the unit for labeling is the segement - so allowing faster
     * processing and involving less features.
     * Lexical features becomes a selection of the first tokens.
     * Possible dictionary flags are at line level (i.e. the line contains a name mention, a place mention, a year, etc.)
     * No layout features, because they have already been taken into account at the segmentation model level.
     */
    private String getFeatureVectorsAsString(List<List<LayoutToken>> segments, 
                                            List<String> sectionTypes,  
                                            List<Boolean> hasDatasets, 
                                            List<String> datasetTypes) {
        // vector for features
        FeaturesVectorDataseer features;
        FeaturesVectorDataseer previousFeatures = null;

        StringBuilder fulltext = new StringBuilder();

        int maxLineLength = 0;
        for(List<LayoutToken> segment : segments) {
            if (segments.size() > maxLineLength)
                maxLineLength = segments.size();
        }

        int m = 0;
        for(List<LayoutToken> segment : segments) {
            int n = 0;
            LayoutToken token = segment.get(n); 
            while(DataseerAnalyzer.DELIMITERS.indexOf(token.getText()) == -1) {
                token = segment.get(n); 
                n++;
            }
            // sanitisation and filtering
            String tokenText = token.getText().trim();
            if ( (tokenText.length() == 0) ||
                (TextUtilities.filterLine(tokenText))) {
                continue;
            }
            features = new FeaturesVectorDataseer();
            features.string = tokenText;

            while(DataseerAnalyzer.DELIMITERS.indexOf(token.getText()) == -1) {
                token = segment.get(n); 
                n++;
            }
            // sanitisation and filtering
            tokenText = token.getText().trim();
            if ( (tokenText.length() > 0) &&
                (!TextUtilities.filterLine(tokenText))) {
                features.secondString = tokenText;
            }

            while(DataseerAnalyzer.DELIMITERS.indexOf(token.getText()) == -1) {
                token = segment.get(n); 
                n++;
            }
            // sanitisation and filtering
            tokenText = token.getText().trim();
            if ( (tokenText.length() > 0) &&
                (!TextUtilities.filterLine(tokenText))) {
                features.thirdString = tokenText;
            }

            features.sectionType = sectionTypes.get(m);
            features.has_dataset = hasDatasets.get(m);
            features.datasetType = datasetTypes.get(m);
            
            //features.punctuationProfile = TextUtilities.punctuationProfile(line);

            if (features.digit == null)
                features.digit = "NODIGIT";


            features.relativeDocumentPosition = FeatureFactory.getInstance()
                    .linearScaling(m, segments.size(), NBBINS_POSITION);
//System.out.println(nn + " " + documentLength + " " + NBBINS_POSITION + " " + features.relativeDocumentPosition); 






            

            if (previousFeatures != null) {
                String vector = previousFeatures.printVector();
                fulltext.append(vector);
            }
            previousFeatures = features;
            m++;
        }
        
        if (previousFeatures != null)
            fulltext.append(previousFeatures.printVector());

        return fulltext.toString();
    }


 
}

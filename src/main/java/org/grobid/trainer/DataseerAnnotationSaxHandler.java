package org.grobid.trainer;

import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.Pair;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.layout.LayoutToken;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * SAX handler for TEI-style annotations. 
 * Dataseer relevant sections are marked at <div> level.
 * Dataseer sentence classification are sentence-level inline annotations <s>.
 * If any, Dataseer entities are inline annotations <rs>.
 *
 * Basically we consider <div> <head> <p> <s> tags in the training corpus. 
 * <head> and <p> are the unit to be labeled. <s> within <p> are classified by the current 
 * Dataseer sentence classifier and used as feature for the <p> level. 
 * <head> content is not classifier. 
 *
 * @author Patrice
 */
public class DataseerAnnotationSaxHandler extends DefaultHandler {

    StringBuffer accumulator = new StringBuffer(); // Accumulate parsed text

    private boolean ignore = true;

    private String currentTag = null;

    private List<List<LayoutToken>> segments;
    private List<String> sectionTypes;
    private List<Integer> nbDatasets;
    private List<String> datasetTypes;
    private List<String> labels;

    private DataseerClassifier dataseerClassifier = null;
    private List<String> currentSentences = null;

    public DataseerAnnotationSaxHandler(DataseerClassifier classifier) {
        segments = new ArrayList<List<LayoutToken>>();
        sectionTypes = new ArrayList<String>();
        nbDatasets = new ArrayList<Integer>();
        datasetTypes = new ArrayList<String>();
        labels = new ArrayList<String>();
        ignore = true;
        dataseerClassifier = classifier;
        currentSentences = new ArrayList<String>();
    }

    public void characters(char[] buffer, int start, int length) {
        if (!ignore)
            accumulator.append(buffer, start, length);
    }

    public String getText() {
        if (accumulator != null) {
            return accumulator.toString().trim();
        } else {
            return null;
        }
    }

    public List<List<LayoutToken>> getSegments() {
        return segments;
    }

    public List<String> getSectionTypes() {
        return sectionTypes;
    }

    public List<Integer> getNbDatasets() {
        return nbDatasets;
    }

    public List<String> getDatasetTypes() {
        return datasetTypes;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void endElement(java.lang.String uri,
                           java.lang.String localName,
                           java.lang.String qName) throws SAXException {
        try {
            if ((qName.equals("head")) || (qName.equals("p"))) {
                writeData(qName);
                currentTag = null;
                currentSentences = new ArrayList<String>();
            } else if (qName.equals("body")) {
                ignore = true;
            } else if (qName.equals("div")) {
                currentTag = "<other>";
            } else if (qName.equals("s")) {
                currentSentences.add(getText());
                accumulator.setLength(0);
            }

        } catch (Exception e) {
//		    e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

    public void startElement(String namespaceURI,
                             String localName,
                             String qName,
                             Attributes atts) throws SAXException {
        try {
            if (qName.equals("body")) {
                ignore = false;
            } else if (qName.equals("space")) {
                accumulator.append(" ");
            } else if (qName.equals("div")) {
                currentTag = "<other>";
                int length = atts.getLength();
                // <div subtype="dataseer">
                // Process each attribute
                for (int i = 0; i < length; i++) {
                    // Get names and values for each attribute
                    String name = atts.getQName(i);
                    String value = atts.getValue(i);

                    if ((name != null) && (value != null)) {
                        if (name.equals("subtype")) {
                            if (value.equals("dataseer")) {
                                currentTag = "<dataseer>";
                            } else {
                                System.out.println("Warning: unknown entity attribute name, " + name);
                            }
                        }
                    }
                }
            } 
            /*else {
                // we have to write first what has been accumulated yet with the upper-level tag
                String text = getText();
                if (text != null) {
                    if (text.length() > 0) {
                        currentTag = "<other>";
                        writeData(qName);
                    }
                }
                accumulator.setLength(0);
            }*/
            
        } catch (Exception e) {
//		    e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

    private void writeData(String qName) {
        if (currentTag == null)
            currentTag = "<other>";
        if ((qName.equals("head")) ||
                (qName.equals("paragraph")) || (qName.equals("p")) ||
                (qName.equals("div"))
                ) {

            if (currentTag == null) {
                return;
            }

            String text = getText();
            if (text == null || text.trim().length() ==0 )
                return;
            
            // we segment the text
            List<LayoutToken> tokenization = DataseerAnalyzer.getInstance().tokenizeWithLayoutToken(text);
            segments.add(tokenization);
            labels.add(currentTag);
            sectionTypes.add(qName);

            // outcome of the data type classifier
            if (qName.equals("paragraph") || (qName.equals("p"))) {
                if (currentSentences != null && currentSentences.size() > 0) {
                    try {
                        String json = dataseerClassifier.classify(currentSentences);
                        System.out.println(currentSentences.toString());
                        System.out.println(json);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            accumulator.setLength(0);
        }
    }

}

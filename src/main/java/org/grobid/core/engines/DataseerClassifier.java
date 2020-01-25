package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.utilities.*;
import org.grobid.core.lexicon.DataseerLexicon;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.engines.tagging.GrobidCRFEngine;
import org.grobid.core.engines.tagging.*;
import org.grobid.core.jni.PythonEnvironmentConfig;
import org.grobid.core.jni.DeLFTClassifierModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xml.sax.InputSource;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import org.w3c.dom.ls.*;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;

import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;

/*import com.googlecode.clearnlp.engine.EngineGetter;
import com.googlecode.clearnlp.reader.AbstractReader;
import com.googlecode.clearnlp.segmentation.AbstractSegmenter;
import com.googlecode.clearnlp.tokenization.AbstractTokenizer;*/

import opennlp.tools.sentdetect.SentenceDetectorME; 
import opennlp.tools.sentdetect.SentenceModel;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * Dataset identification.
 *
 * @author Patrice
 */
public class DataseerClassifier {
    private static final Logger logger = LoggerFactory.getLogger(DataseerClassifier.class);

    private static volatile DataseerClassifier instance;

    // components for sentence segmentation
    private SentenceDetectorME detector = null;
    private static String openNLPModelFile = "resources/openNLP/en-sent.bin";

    private static Engine engine = null; 

    private static List<String> textualElements = Arrays.asList("p"); //, "abstract", "figDesc");

    // map of classification models (binay, first-level, etc.)
    private Map<String,DeLFTClassifierModel> models = null;

    private DeLFTClassifierModel classifierBinary = null;
    private DeLFTClassifierModel classifierFirstLevel = null;

    public static DataseerClassifier getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new DataseerClassifier();
    }

    //private DataseerLexicon dataseerLexicon = null;

    private DataseerClassifier() {
        //dataseerLexicon = DataseerLexicon.getInstance();
        try {
            // force loading of DeLFT and Wapiti lib without conflict
            GrobidProperties.getInstance();

            // actual loading will be made at JEP initialization, so we just need to add the path in the 
            // java.library.path (JEP will anyway try to load from java.library.path, so explicit file 
            // loading here will not help)
            try {
                logger.info("Loading external native sequence labelling library");
                logger.debug(LibraryLoader.getLibraryFolder());

                File libraryFolder = new File(LibraryLoader.getLibraryFolder());
                if (!libraryFolder.exists() || !libraryFolder.isDirectory()) {
                    logger.error("Unable to find a native sequence labelling library: Folder "
                        + libraryFolder + " does not exist");
                    throw new RuntimeException(
                        "Unable to find a native sequence labelling library: Folder "
                            + libraryFolder + " does not exist");
                }

                File[] wapitiLibFiles = libraryFolder.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.startsWith(LibraryLoader.WAPITI_NATIVE_LIB_NAME);
                    }
                });

                if (isEmpty(wapitiLibFiles)) {
                    logger.info("No wapiti library in the Grobid home folder");
                } else {
                    logger.info("Loading Wapiti native library...");
                    // if DeLFT will be used, we must not load libstdc++, it would create a conflict with tensorflow libstdc++ version
                    // so we temporary rename the lib so that it is not loaded in this case
                    // note that we know that, in this case, the local lib can be ignored because as DeFLT and tensorflow are installed
                    // we are sure that a compatible libstdc++ lib is installed on the system and can be dynamically loaded

                    String libstdcppPath = libraryFolder.getAbsolutePath() + File.separator + "libstdc++.so.6";
                    File libstdcppFile = new File(libstdcppPath);
                    if (libstdcppFile.exists()) {
                        File libstdcppFileNew = new File(libstdcppPath + ".new");
                        libstdcppFile.renameTo(libstdcppFileNew);
                    
                    }
                    try {
                        System.load(wapitiLibFiles[0].getAbsolutePath());
                    } finally {
                        // restore libstdc++
                        String libstdcppPathNew = libraryFolder.getAbsolutePath() + File.separator + "libstdc++.so.6.new";
                        File libstdcppFileNew = new File(libstdcppPathNew);
                        if (libstdcppFileNew.exists()) {
                            libstdcppFile = new File(libraryFolder.getAbsolutePath() + File.separator + "libstdc++.so.6");
                            libstdcppFileNew.renameTo(libstdcppFile);
                        }
                    }
                }

                logger.info("Loading JEP native library for DeLFT... " + libraryFolder.getAbsolutePath());
                LibraryLoader.addLibraryPath(libraryFolder.getAbsolutePath());

            } catch (Exception e) {
                throw new GrobidException("Loading JEP native library for DeLFT failed", e);
            }

            //Loading sentence detector model (hope they are thread safe!)
            InputStream inputStream = new FileInputStream(openNLPModelFile); 
            SentenceModel model = new SentenceModel(inputStream);
            detector = new SentenceDetectorME(model);

            // grobid
            engine = GrobidFactory.getInstance().createEngine();

            // Datatype classifier via DeLFT
            classifierBinary = new DeLFTClassifierModel("dataseer-binary", "gru");
            classifierFirstLevel = new DeLFTClassifierModel("dataseer-first", "gru");

        } catch (FileNotFoundException e) {
            throw new GrobidException("Cannot initialise tokeniser ", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot initialise tokeniser ", e);
        }
    }

    /**
     * Classify a simple piece of text
     * @return JSON string
     */
    public String classify(String text) throws Exception {
        if (StringUtils.isEmpty(text))
            return null;
        List<String> texts = new ArrayList<String>();
        texts.add(text);
        return classify(texts);
    }

    /**
     * Classify an array of texts
     * @return JSON string
     */
    public String classify(List<String> texts) throws Exception {
        if (texts == null || texts.size() == 0)
            return null;
        logger.info("classify: " + texts.size() + " sentence(s)");
        ObjectMapper mapper = new ObjectMapper();
        
        String the_json = classifierBinary.classify(texts);
        // first pass to select texts to be cascaded to next level
        List<String> cascaded_texts = new ArrayList<String>();
        JsonNode root = null;
        if (the_json != null && the_json.length() > 0) {
            root = mapper.readTree(the_json);
            JsonNode classificationsNode = root.findPath("classifications");
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                Iterator<JsonNode> ite = classificationsNode.elements();
                while (ite.hasNext()) {
                    JsonNode classificationNode = ite.next();
                    JsonNode datasetNode = classificationNode.findPath("dataset");
                    JsonNode noDatasetNode = classificationNode.findPath("no_dataset");

                    if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                        (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                        double probDataset = datasetNode.asDouble();
                        double probNoDataset = noDatasetNode.asDouble();

                        //System.out.println(probDataset + " " + probNoDataset);
                        if (probDataset > probNoDataset) {
                            JsonNode textNode = classificationNode.findPath("text");
                            cascaded_texts.add(textNode.asText());
                        } 

                        // rename "dataset" attribute to avoid confusion with "Dataset" type of the taxonomy
                        ((ObjectNode)classificationNode).put("has_dataset", probDataset);
                        ((ObjectNode)classificationNode).remove("dataset");
                    }
                }
            }
        }
        //System.out.println("cascaded classify: " + cascaded_texts.size() + " sentences");
        String cascaded_json = null;
        JsonNode rootCascaded = null;
        if (cascaded_texts.size() > 0) {
            cascaded_json = classifierFirstLevel.classify(cascaded_texts);
            if (cascaded_json != null && cascaded_json.length() > 0)
                rootCascaded = mapper.readTree(cascaded_json);
        }

        if (rootCascaded == null) {
            return this.shadowModelName(the_json);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{\n\t\"model\": \"dataseer\",\n\t\"software\": \"DeLFT\",\n\t\"date\": \"" + 
            this.getISO8601Date() + "\",\n\t\"classifications\": [");

        boolean first = true;
        // second pass to inject additional results
        if (root != null && rootCascaded != null) {
            JsonNode classificationsNode = root.findPath("classifications");
            JsonNode classificationsCascadedNode = rootCascaded.findPath("classifications");
            if ((classificationsNode != null) && (!classificationsNode.isMissingNode()) && 
                (classificationsCascadedNode != null) && (!classificationsCascadedNode.isMissingNode())) {
                Iterator<JsonNode> ite = classificationsNode.elements();
                Iterator<JsonNode> iteCascaded = classificationsCascadedNode.elements();
                while (ite.hasNext()) {
                    JsonNode classificationNode = ite.next();
                    JsonNode datasetNode = classificationNode.findPath("has_dataset");
                    JsonNode noDatasetNode = classificationNode.findPath("no_dataset");

                    if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                        (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                        double probDataset = datasetNode.asDouble();
                        double probNoDataset = noDatasetNode.asDouble();

                        //System.out.println(probDataset + " " + probNoDataset);
                        if (probDataset > probNoDataset) {
                            JsonNode textNode = classificationNode.findPath("text");
                            if (iteCascaded.hasNext()) {
                                JsonNode classificationCascadedNode = iteCascaded.next();
                                // inject dataset/no_dataset probabilities as extra-information relevant for post--processing
                                ((ObjectNode)classificationCascadedNode).put("has_dataset", probDataset);
                                ((ObjectNode)classificationCascadedNode).put("no_dataset", probNoDataset);
                                if (first)
                                    first = false;
                                else
                                    builder.append(",");
                                builder.append("\n\t\t");
                                builder.append(this.prettyPrintJsonNode(classificationCascadedNode, mapper));
                            }
                        } else {
                            if (first)
                                first = false;
                            else
                                builder.append(",");
                            builder.append("\n\t\t");
                            builder.append(this.prettyPrintJsonNode(classificationNode, mapper));
                        }
                    }
                }
            }
        }
        builder.append("\n\t]\n}");

        if (the_json != null) {
            // replace the model explitely used by a more general "dataseer"
            // final beautifier
            String finalJson = builder.toString();
            return prettyPrintJsonString(finalJson, mapper);
        }
        else
            return null;
    }

    private String shadowModelName(String the_json) {
        the_json = the_json.replace("\"model\": \"dataseer-binary\",", "\"model\": \"dataseer\",");
        return the_json.replace("\"model\": \"dataseer-first\",", "\"model\": \"dataseer\",");
    } 

    public String prettyPrintJsonNode(JsonNode jsonNode, ObjectMapper mapper) {
        if (jsonNode == null || jsonNode.isMissingNode())
            return null;
        try {
            Object json = mapper.readValue(jsonNode.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String prettyPrintJsonString(String json, ObjectMapper mapper) {
        try {
            Object root = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getISO8601Date() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); 
        return sdf.format(date);
    }

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEIString(String xmlString) throws Exception {
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();           
            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(xmlString)));
            //document.getDocumentElement().normalize();
            tei = processTEIDocument(document);
        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } 
        return tei;
    }
    

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEI(String filePath, boolean avoidDomParserBug) throws Exception {
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(filePath), UTF_8);
            if (avoidDomParserBug)
                tei = avoidDomParserAttributeBug(tei);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();
            tei = processTEIDocument(document);
            if (avoidDomParserBug)
                tei = restoreDomParserAttributeBug(tei); 

        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } 
        return tei;
    }

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEIDocument(org.w3c.dom.Document document) throws Exception {
        String tei = null;
        Element root = document.getDocumentElement();
        segment(document, root);
        // augment sentences with dataseer classification information
        enrich(document, root);
        tei = serialize(document, null);
        return tei;
    }

    /**
     * Process a JATS document and enrich with Dataseer information as a TEI document.
     * Transformation of the JATS/NLM document is realised thanks to Pub2TEI 
     * (https://github.com/kermitt2/pub2tei) 
     * 
     * @return enriched TEI string
     */
    public String processJATS(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists())
            return null;
        String fileName = file.getName();
        String tei = null;
        String newFilePath = null;
        try {
            File tmpFile = GrobidProperties.getInstance().getTempPath();
            newFilePath = ArticleUtilities.applyPub2TEI(filePath, 
                tmpFile.getPath() + "/" + fileName.replace(".xml", ".tei.xml"), 
                DataseerProperties.getPub2TEIPath());
            //System.out.println(newFilePath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            tei = FileUtils.readFileToString(new File(newFilePath), UTF_8);
            //if (avoidDomParserBug)
            //    tei = avoidDomParserAttributeBug(tei);

            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(tei)));
            //document.getDocumentElement().normalize();
            tei = processTEIDocument(document);
            //if (avoidDomParserBug)
            //    tei = restoreDomParserAttributeBug(tei); 

        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            if (newFilePath != null) {
                File newFile = new File(newFilePath);
                IOUtilities.removeTempFile(newFile);
            }
        }
        return tei;
    }


    private void segment(org.w3c.dom.Document doc, Node node) {
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node n = children.item(i);
            if ( (n.getNodeType() == Node.ELEMENT_NODE) && 
                 (textualElements.contains(n.getNodeName())) ) {
                // text content
                //String text = n.getTextContent();
                StringBuffer textBuffer = new StringBuffer();
                NodeList childNodes = n.getChildNodes();
                for(int y=0; y<childNodes.getLength(); y++) {
                    textBuffer.append(serialize(doc, childNodes.item(y)));
                    textBuffer.append(" ");
                }
                String text = textBuffer.toString();
                String theSentences[] = detector.sentDetect(text);

                // we're making a first pass to ensure that there is no element broken by the segmentation
                List<String> sentences = new ArrayList<String>();
                List<String> toConcatenate = new ArrayList<String>();
                for(String sent : theSentences) {
                    //System.out.println("new chunk: " + sent);
                    String newSent = sent;
                    if (toConcatenate.size() != 0) {
                        StringBuffer conc = new StringBuffer();
                        for(String concat : toConcatenate) {
                            conc.append(concat);
                            conc.append(" ");
                        }
                        newSent = conc.toString() + sent;
                    }
                    String fullSent = "<s xmlns=\"http://www.tei-c.org/ns/1.0\">" + newSent + "</s>";
                    boolean fail = false;
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(fullSent)));                
                    } catch(Exception e) {
                        fail = true;
                    }
                    if (fail)
                        toConcatenate.add(sent);
                    else {
                        sentences.add(fullSent);
                        toConcatenate = new ArrayList<String>();
                    }
                }

                List<Node> newNodes = new ArrayList<Node>();
                for(String sent : sentences) {
                    //System.out.println("-----------------");
                    sent = sent.replace("\n", " ");
                    sent = sent.replaceAll("( )+", " ");
                
                    //Element sentenceElement = doc.createElement("s");                        
                    //sentenceElement.setTextContent(sent);
                    //newNodes.add(sentenceElement);

                    //System.out.println(sent);  

                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(sent)));
                        //d.getDocumentElement().normalize();
                        Node newNode = doc.importNode(d.getDocumentElement(), true);
                        newNodes.add(newNode);
                        //System.out.println(serialize(doc, newNode));
                    } catch(Exception e) {

                    }
                }

                // remove old nodes 
                while (n.hasChildNodes())
                    n.removeChild(n.getFirstChild());

                // and add new ones
                for(Node theNode : newNodes) 
                    n.appendChild(theNode);

            } else if (n.getNodeType() == Node.ELEMENT_NODE) {
                segment(doc, (Element) n);
            }
        }
    }

    private static String specialHeader = "materials and methods";

    private void enrich(org.w3c.dom.Document doc, Node node) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> mapSentenceJsonResult = new TreeMap<String, JsonNode>();

        // build the list of sections
        List<Boolean> relevantSections = null;
        List<String> segments = new ArrayList<String>();
        List<String> sectionTypes = new ArrayList<String>();
        List<Integer> nbDatasets =new ArrayList<Integer>();
        List<String> datasetTypes = new ArrayList<String>();
        NodeList sectionList = doc.getElementsByTagName("div");
        for (int i = 0; i < sectionList.getLength(); i++) {
            Element sectionElement = (Element) sectionList.item(i);

            // head element (unique, but not mandatory)
            Element headElement = this.getFirstDirectChild(sectionElement, "head");
            if (headElement != null) {
                segments.add(headElement.getTextContent());
                sectionTypes.add("head");
                nbDatasets.add(0);
                datasetTypes.add("no_dataset");
            }

            // the <p> elements 
            for(Node child = sectionElement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element && "p".equals(child.getNodeName())) {
                    Element childElement = (Element)child;
                    segments.add(childElement.getTextContent());
                    sectionTypes.add("p");

                    // get the sentences elements
                    List<String> localSentences = new ArrayList<String>();
                    for(Node subchild = childElement.getFirstChild(); subchild != null; subchild = subchild.getNextSibling()) {
                        if (subchild instanceof Element && "s".equals(subchild.getNodeName())) {
                            Element subchildElement = (Element)subchild;
                            localSentences.add(subchildElement.getTextContent());
                        }
                    }
                    int nbLocalDatasets = 0;
                    String mainDataset = null;
                    try {
                        String localJson = this.classify(localSentences);

                        List<Boolean> datasetSentences = new ArrayList<Boolean>();
                        if (localJson != null && localJson.length() > 0) {
                            JsonNode root = mapper.readTree(localJson);
                            JsonNode classificationsNode = root.findPath("classifications");
                            if ((classificationsNode != null) && (!classificationsNode.isMissingNode())) {
                                Iterator<JsonNode> ite = classificationsNode.elements();
                                while (ite.hasNext()) {
                                    JsonNode classificationNode = ite.next();
                                    JsonNode datasetNode = classificationNode.findPath("has_dataset");
                                    JsonNode noDatasetNode = classificationNode.findPath("no_dataset");
                                    JsonNode textNode = classificationNode.findPath("text");

                                    Boolean localResult = new Boolean(false);
                                    if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                                        (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                                        double probDataset = datasetNode.asDouble();
                                        double probNoDataset = noDatasetNode.asDouble();

                                        // we consider enrichment only in the case a dataset is more likely
                                        if (probDataset > probNoDataset && probDataset > 0.9)
                                            nbLocalDatasets++;

                                        // save results
                                        mapSentenceJsonResult.put(textNode.asText(), classificationNode);
                                    }
                                }
                            }
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }

                    nbDatasets.add(nbLocalDatasets);
                    datasetTypes.add("no_dataset");
                }
            }
            relevantSections = 
                DataseerParser.getInstance().processingText(segments, sectionTypes, nbDatasets, datasetTypes);
        }

        sectionList = doc.getElementsByTagName("div");
        int dataSetId = 1;
        int relevantSectionIndex = 0;
        for (int i = 0; i < sectionList.getLength(); i++) {
            Element sectionElement = (Element) sectionList.item(i);
            boolean relevantSection = false;

            // head element (unique, but not mandatory)
            Element headElement = this.getFirstDirectChild(sectionElement, "head");
            if (headElement != null) {
                relevantSection = relevantSections.get(relevantSectionIndex);
                relevantSectionIndex++;
            }

            // the <p> elements 
            for(Node child = sectionElement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element && "p".equals(child.getNodeName())) {
                    boolean localRelevantSection = relevantSections.get(relevantSectionIndex);
                    if (localRelevantSection)
                        relevantSection = true;
                    relevantSectionIndex++;
                }

            }

            // do we consider this section?
            if (!relevantSection)
                continue;

            // if we consider this section, we get back the classification of the sentences present in it and
            // update the <div> level accordingly
            for(Node child = sectionElement.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element && "p".equals(child.getNodeName())) {
                    Element childElement = (Element)child;
                    // get the sentences elements
                    for(Node subchild = childElement.getFirstChild(); subchild != null; subchild = subchild.getNextSibling()) {
                        if (subchild instanceof Element && "s".equals(subchild.getNodeName())) {
                            Element subchildElement = (Element)subchild;
                            
                            String localSentence = subchildElement.getTextContent();
                            JsonNode classificationNode = mapSentenceJsonResult.get(localSentence);

                            if (classificationNode != null && (!classificationNode.isMissingNode())) {
                                JsonNode datasetNode = classificationNode.findPath("has_dataset");
                                JsonNode noDatasetNode = classificationNode.findPath("no_dataset");

                                if ((datasetNode != null) && (!datasetNode.isMissingNode()) &&
                                    (noDatasetNode != null) && (!noDatasetNode.isMissingNode()) ) {
                                    double probDataset = datasetNode.asDouble();
                                    double probNoDataset = noDatasetNode.asDouble();

                                    // we consider enrichment only in the case a dataset is more likely
                                    if (probDataset > probNoDataset && probDataset > 0.9) {
                                        // we get the best dataset type Prediction
                                        Pair<String, Double> bestDataTypeWithProb = this.getBestDataType(classificationNode);
                                        if (bestDataTypeWithProb != null) {
                                            // annotation will look like this: <s id="dataset-1" type="Generic data">
                                            // or if existing dataset: corresp=\"#dataset- + dataSetId\"
                                            Element sentenceElement = subchildElement;

                                            sentenceElement.setAttribute("id","dataset-" + dataSetId);
                                            sentenceElement.setAttribute("type", bestDataTypeWithProb.getLeft());
                                            sentenceElement.setAttribute("cert", bestDataTypeWithProb.getRight().toString());
                                            dataSetId++;

                                            // we also need to add a dataseer subtype attribute to the parent <div>
                                            Node currentNode = sentenceElement;
                                            while(currentNode != null) {
                                                currentNode = currentNode.getParentNode();
                                                if (currentNode != null && 
                                                    currentNode instanceof Element &&
                                                    !(currentNode.getParentNode() instanceof Document) && 
                                                    ((Element)currentNode).getTagName().equals("div")) {
                                                    ((Element)currentNode).setAttribute("subtype", "dataseer");
                                                    currentNode = null;
                                                }

                                                if (currentNode != null && (currentNode.getParentNode() instanceof Document))
                                                    currentNode = null;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static Element getFirstDirectChild(Element parent, String name) {
        for(Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && name.equals(child.getNodeName())) 
                return (Element) child;
        }
        return null;
    }

    private static String getUpperHeaderSection(Element element) {
        String header = null;
        Node currentNode = element;
        while(currentNode != null) {
            currentNode = currentNode.getParentNode();
            if (currentNode != null && 
                currentNode instanceof Element &&
                !(currentNode.getParentNode() instanceof Document) && 
                ((Element)currentNode).getTagName().equals("div")) {
                Element headElement = getFirstDirectChild((Element)currentNode, "head");
                if (headElement != null) {
                    header = headElement.getTextContent();   
                    currentNode = null;
                }
            }

            if (currentNode != null && (currentNode.getParentNode() instanceof Document))
                currentNode = null;
        }
        return header;
    }

    public static String serialize(org.w3c.dom.Document doc, Node node) {
        DOMSource domSource = null;
        String xml = null;
        try {
            if (node == null) {
                domSource = new DOMSource(doc);
            } else {
                domSource = new DOMSource(node);
            }
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (node != null)
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(domSource, result);
            xml = writer.toString();
        } catch(TransformerException ex) {
            ex.printStackTrace();
        }
        return xml;
    }

    public String serializeLs(org.w3c.dom.Document doc)    {
        DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
        LSSerializer lsSerializer = domImplementation.createLSSerializer();
        return lsSerializer.writeToString(doc);   
    }

    private Pair<String, Double> getBestDataType(JsonNode classificationsNode) {
        Iterator<Map.Entry<String,JsonNode>> ite = classificationsNode.fields();
        String bestDataType = null;
        double bestProb = 0.0;
        while (ite.hasNext()) {
            Map.Entry<String, JsonNode> entry = ite.next(); 
            String className = entry.getKey();
            if (className.equals("has_dataset") || className.equals("no_dataset"))
                continue;

            JsonNode valNode = entry.getValue();
            double prob = valNode.asDouble();

            if (prob > bestProb) {
                bestProb = prob;
                bestDataType = className;
            }
        }
        return Pair.of(bestDataType, new Double(bestProb));
    }


    /**
     *  XML is always full of bad surprises. The following document:
     * <?xml version="1.0" encoding="UTF-8"?>
     * <a>
     * <p>
     * <c toto="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution License</c>
     * </p>
     * </a>
     * results in [Fatal Error] :1:94: Element type "ref" must be followed by either attribute specifications, ">" or "/>".
     * or [Fatal Error] :1:70: The element type "c" must be terminated by the matching end-tag "</c>". 
     * It appears that removing the dots in the attribute value avoid the parsing error (it doesn't make sense of course, 
     * but ok...).
     * So we temporary replace the dot in the attribute values of <ref> by dummy &#x02ADB;, and restore them afterwards.
     */
    public String avoidDomParserAttributeBug(String xml) {
        //System.out.println(xml);
        String newXml = xml.replaceAll("(<ref .*)\\.(.*>)", "$1&#x02ADB;$2");
        newXml = newXml.replaceAll("(<formula .*)\\.(.*>)", "$1&#x02ADB;$2");
        while(!newXml.equals(xml)) {
            xml = newXml;
            newXml = xml.replaceAll("(<ref .*)\\.(.*>)", "$1&#x02ADB;$2");
            newXml = newXml.replaceAll("(<formula .*)\\.(.*>)", "$1&#x02ADB;$2");
        }
        xml = newXml;
        //System.out.println(xml);
        return xml;
    }

    public String restoreDomParserAttributeBug(String xml) {
        xml = xml.replace("&#x02ADB;", ".");
        return xml;
    }

    /**
     * Convert a PDF into TEI and enrich the TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processPDF(String filePath) throws Exception {
        // convert PDF into structured TEI thanks to GROBID

        // TBD: review arguments, no need for images, annotations, outline
        GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
            .consolidateHeader(1)
            .consolidateCitations(0)
            .build();
        String tei = engine.fullTextToTEI(new File(filePath), config);
        return processTEIString(tei);
    }

}
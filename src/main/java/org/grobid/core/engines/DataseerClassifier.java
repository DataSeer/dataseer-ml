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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;

/*import com.googlecode.clearnlp.engine.EngineGetter;
import com.googlecode.clearnlp.reader.AbstractReader;
import com.googlecode.clearnlp.segmentation.AbstractSegmenter;
import com.googlecode.clearnlp.tokenization.AbstractTokenizer;*/

import opennlp.tools.sentdetect.SentenceDetectorME; 
import opennlp.tools.sentdetect.SentenceModel;

/**
 * Dataset identification.
 *
 * @author Patrice
 */
public class DataseerClassifier {
    private static final Logger logger = LoggerFactory.getLogger(DataseerClassifier.class);

    private static volatile DataseerClassifier instance;

    // components for sentence segmentation
    private SentenceDetectorME detector =null;
    private static String openNLPModelFile = "resources/openNLP/en-sent.bin";

    private static Engine engine = null; 

    private static List<String> textualElements = Arrays.asList("p"); //, "abstract", "figDesc");

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

    private DataseerLexicon dataseerLexicon = null;

    private DataseerClassifier() {
        dataseerLexicon = DataseerLexicon.getInstance();
        try {
            LibraryLoader.load();

            //Loading sentence detector model (hope they are thread safe!)
            InputStream inputStream = new FileInputStream(openNLPModelFile); 
            SentenceModel model = new SentenceModel(inputStream);
            detector = new SentenceDetectorME(model);

            // grobid
            GrobidProperties.getInstance();
            engine = GrobidFactory.getInstance().createEngine();
        } catch (FileNotFoundException e) {
            throw new GrobidException("Cannot initialise tokeniser ", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot initialise tokeniser ", e);
        }
    }

    /**
     * Extract all Dataseer Objects from a simple piece of text
     * @return JSON string
     */
    public String processText(String text) throws Exception {
        StringBuffer sb = new StringBuffer();
        sb.append("{ \"dataset-type\"}");
        return sb.toString();
    }

    /**
     * Enrich a TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processTEIString(String xmlString) throws Exception {
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();           
            org.w3c.dom.Document document = builder.parse(new InputSource(new StringReader(xmlString)));
            document.getDocumentElement().normalize();
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
    public String processTEI(String filePath) throws Exception {
        String tei = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
           
            org.w3c.dom.Document document = builder.parse(new File(filePath));
            document.getDocumentElement().normalize();
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
    public String processTEIDocument(org.w3c.dom.Document document) throws Exception {
        String tei = null;
        Element root = document.getDocumentElement();
        segment(document, root);
        tei = serialize(document, null);
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
                    String fullSent = "<s>" + newSent + "</s>";
                    //System.out.println("try: " + fullSent);
                    boolean fail = false;
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
                        org.w3c.dom.Document d = factory.newDocumentBuilder().parse(new InputSource(new StringReader(sent)));
                        d.getDocumentElement().normalize();
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

    public String serialize(org.w3c.dom.Document doc, Node node) {
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


    /**
     * Convert a PDF into TEI and enrich the TEI document with Dataseer information
     * @return enriched TEI string
     */
    public String processPDF(String filePath) throws Exception {
        // convert PDF into structured TEI thanks to GROBID
        GrobidAnalysisConfig config = 
            new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder().build();
        // TBD: review arguments, no need for images, annotations, outline
        String tei = engine.fullTextToTEI(new File(filePath), config);
        return processTEIString(tei);
    }

}
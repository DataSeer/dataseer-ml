package org.grobid.trainer;

import org.grobid.core.analyzers.DataseerAnalyzer;
import org.grobid.core.engines.DataseerClassifier;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.ArticleUtilities;

import org.grobid.core.analyzers.GrobidAnalyzer;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.Engine;
import org.grobid.core.engines.FullTextParser;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.lang.Language;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;

import org.apache.commons.io.*;
import org.apache.commons.csv.*;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URI;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * This class aims at converting annotations in .csv format from the original 
 * dataseer dataset into annotated XML files (at document level) usable for training 
 * text mining tools and readable by humans. 
 *
 * We need in particular to re-align the content of the original document which
 * has been annotated (e.g. a PMC article) with the "quotes" and strings available
 * in the .csv stuff. This is not always straightforward because: 
 * 
 * - the strings in the csv files has been cut and paste directly from the PDF 
 *   document, which is more noisy than what we can get from GROBID PDF parsing 
 *   pipeline,
 * - some annotations refers to  unlocated information present in the document and 
 *   we need some global document analysis to try to related the annotations with 
 *   the right document content.
 *
 * Example command line:
 * ./gradlew annotated_corpus_generator_csv -Ppdf=-Ppdf=/mnt/data/resources/plos/0/tei/ 
 * -Pcsv=resources/dataset/dataseer/csv/ -Pxml=resources/dataset/dataseer/corpus/
 *
 * @author Patrice
 */
public class AnnotatedCorpusGeneratorCSV {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatedCorpusGeneratorCSV.class);

    static Charset UTF_8 = Charset.forName("UTF-8"); // StandardCharsets.UTF_8

    private ArticleUtilities articleUtilities = new ArticleUtilities();

    /**
     * Start the conversion/fusion process for generating MUC-style annotated XML documents
     * from PDF, parsed by GROBID core, and dataseer dataset  
     */
    public void process(String documentPath, String csvPath, String xmlPath) throws IOException {

        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, DataseerAnnotation> annotations = new HashMap<String, DataseerAnnotation>();

        importCSVFiles(csvPath, documents, annotations);

        System.out.println("\n" + annotations.size() + " total annotations");
        System.out.println(documents.size() + " total annotated documents");  
        if (!documentPath.endsWith("/"))
            documentPath += "/";

        DataseerClassifier dataseer = DataseerClassifier.getInstance();

        // go thought all annotated documents 
        int m = 0;
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            if (m > 100) {
                break;
            }
            m++;
            AnnotatedDocument doc = entry.getValue();
            String doi = doc.getDoi();

            //System.out.println(doi);

            // get file name from DOI

            // temporary: only process PLOS, e.g. plos/0/journal.pbio.0020190.xml
            if (doi.indexOf("10.1371/") == -1)
                continue;

            int ind1 = doi.indexOf("journal");
            String fileName = doi.substring(ind1);
            int ind2 = fileName.lastIndexOf(".");
            String plosPath = documentPath + fileName + ".xml"; 

            System.out.println(plosPath);
            // get the XML full text if available, otherwise PDF
            File f = new File(plosPath);
            if (!f.exists()) {
                System.out.println("TEI XML of the PLOS article does not exist: " + plosPath);
                System.out.println("moving to next article...");
                continue;
            }   

            try {
                // segment into sentence
                String segmentedTEI = dataseer.processTEI(plosPath);

                // match sentence and inject attributes to sentence tags
                for(DataseerAnnotation annotation : doc.getAnnotations()) {
                    String sentence = annotation.getContext();
                    if (segmentedTEI.indexOf(sentence) != -1) {
                        System.out.println("matched sentence!");
                    } else {
                        System.out.println("unmatched sentence: " + sentence);
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }


        }

    }


    private void importCSVFiles(String csvPath, Map<String, AnnotatedDocument> documents, Map<String, DataseerAnnotation> annotations) {
        // process is driven by what's available in the dataseer dataset
        File dataseerRoot = new File(csvPath);
        // if the given root is the dataseer repo root, we go down to data/ and then csv_dataset 
        // (otherwise we assume we are already under data/csv_dataset)
        // todo


        File[] refFiles = dataseerRoot.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".csv");
            }
        });

        if (refFiles == null) {
            logger.warn("We found no .csv file to process");
            return;
        }

        // this csv file gives information on the article set to which each article belongs to 
        File dataseerArticles = new File(csvPath + File.separator + "All2000_for_Patrice_16th_June-xlsx.csv");

        try {
            CSVParser parser = CSVParser.parse(dataseerArticles, UTF_8, CSVFormat.RFC4180);
            // schema is:
            // Journal,Article #,Manuscript ID or DOI,dataset number,Full MeSH data type,Section,Subsection title,Page number,Column number,
            // Data paragraph,Data Keyword,Data action word,Specialist equipment,Notes
            // e.g.
            // PLOS ONE,1.0,https://doi.org/10.1371/journal.pone.0198270,1.0,Dataset:Existing dataset,Materials and methods,
            // Architecture,6.0,1.0,"For this phase, each dataset was downloaded from curated sources and was annotated with
            // ontology terms URIs by reusing the ontology fields when provided by the original source.",Dataset,was downloaded,,

            boolean start = true;
            for (CSVRecord csvRecord : parser) {
                if (start) {
                    start = false;
                    continue;
                }
                DataseerAnnotation annotation = null;
                String attribute = null;
                String documentId = null;
                for(int i=0; i<csvRecord.size(); i++) {
                    String value = csvRecord.get(i);
                    if ( (value.trim().length() == 0) || (value.trim().equals("NA")) )
                        continue;
                    value = cleanValue(value);
                    if (i == 0) {
                        annotation = new DataseerAnnotation();
                        annotation.setCollectionID(value);
                    } else if (i == 1) {
                        documentId = value.replace(".0", "");
                        annotation.setDocumentId(documentId);
                        annotations.put(value, annotation);
                        AnnotatedDocument doc = documents.get(documentId);
                        if (doc == null) {
                            doc = new AnnotatedDocument();
                            doc.setDocumentId(documentId);
                            documents.put(documentId, doc);
                        }
                        doc.addAnnotation(annotation);
                    } else if (i == 2) {
                        AnnotatedDocument doc = documents.get(documentId);
                        if (doc.getDoi() == null) {
                            if (value.startsWith("https://doi.org/"))
                                value = value.replace("https://doi.org/", "");
                            doc.setDoi(value);
                        }
                    } else if (i == 3) {
                        annotation.setDatasetId(value.replace(".0", ""));
                    } else if (i == 4) {
                        annotation.setRawDataType(value);
                    } else if (i == 5) {
                        annotation.setSection(value);
                    } else if (i == 6) {
                        annotation.setSubsection(value);
                    } else if (i == 7) {
                        annotation.setPage(value.replace(".0", ""));
                    } else if (i == 9) {
                        annotation.setContext(value);
                    } else if (i == 10) {
                        annotation.setDataKeyword(value);
                    } else if (i == 11) {
                        annotation.setDataAction(value);
                    } else if (i == 12) {
                        annotation.setAcquisitionEquipment(value);
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static String cleanValue(String value) {
        value = value.trim();
        value = value.replace("\n", " ");
        if (value.startsWith("\""))
            value = value.substring(1,value.length());
        if (value.endsWith("\""))
            value = value.substring(0,value.length()-1);
        value = value.replaceAll(" +", " ");
        return value.trim();
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
       
        // we are expecting three arguments, absolute path to the original fulltext
        // documents, absolute path to the  csv files and path where to put the generated 
        // XML files 

        if (args.length != 3) {
            System.err.println("Usage: command [absolute path to the original fulltexts] [absolute path to the dataseer root data in csv] [output for the generated XML files]");
            System.exit(-1);
        }

        String documentPath = args[0];
        File f = new File(documentPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to full text directory does not exist or is invalid: " + documentPath);
            System.exit(-1);
        }

        String csvPath = args[1];
        f = new File(csvPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to dataseer data csv directory does not exist or is invalid: " + csvPath);
            System.exit(-1);
        }

        String xmlPath = args[2];
        f = new File(xmlPath);
        if (!f.exists() || !f.isDirectory()) {
            System.out.println("XML output directory path does not exist, so it will be created");
            new File(xmlPath).mkdirs();
        }       

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV();
        try {
            converter.process(documentPath, csvPath, xmlPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
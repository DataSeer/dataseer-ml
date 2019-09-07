package org.grobid.service;

/**
 * This interface only contains the path extensions for accessing the dataseer module service.
 *
 * @author Patrice
 *
 */
public interface DataseerPaths {
    /**
     * path extension for dataseer service.
     */
    public static final String PATH_DATASEER = "/";
    
    /**
     * path extension for processing a textual sentence input.
     */
    public static final String PATH_DATASEER_SENTENCE = "processDataseerSentence";

    /**
     * path extension for processing a TEI file 
     * (for instance produced by GROBID or Pub2TEI).
     */
    public static final String PATH_DATASEER_TEI = "processDataseerTEI";

    /**
     * path extension for processing a JATS file.
     */
    public static final String PATH_DATASEER_JATS = "processDataseerJATS";

    /**
     * path extension for processing a PDF file, which will include its conversion 
     * into TEI via GROBID.
     */
    public static final String PATH_DATASEER_PDF = "processDataseerPDF";

    /**
     * path extension for annotating a PDF file with the dataset-relevant sentences.
     */
    public static final String PATH_ANNOTATE_DATASEER_PDF = "annotateDataseerPDF";
}

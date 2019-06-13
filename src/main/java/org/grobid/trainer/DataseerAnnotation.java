package org.grobid.trainer;

import java.util.*;

import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.OffsetPosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * POJO for dataseer annotation, filled by parsing original dataseer dataset, with JSON serialization. 
 *
 * @author Patrice
 */
public class DataseerAnnotation extends Annotation {
    private static final Logger logger = LoggerFactory.getLogger(DataseerAnnotation.class);

    enum AnnotationType {
        DATASET, DATA_ACQ, OTHER;
    }

    private String identifier = null;

    private String annotatorID = null;

    private AnnotationType type = null;

    private String datasetMention = null;

    private String context = null;

    // page as provided by the original dataset
    private int page = -1;

    private String url = null;

    public String getIdentifier() {
        return this.identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getAnnotatorID() {
        return this.annotatorID;
    }

    public void setAnnotatorID(String annotatorID) {
        this.annotatorID = annotatorID;
    }

    public String getField(String field) {
        switch (field) {
            case "dataset":
                return this.datasetMention;
            case "url":
                return this.url;
            case "quote":
                return this.context;
        }
        return null;
    }

    public AnnotationType getType() {
        return this.type;
    }

    public void setType(AnnotationType type) {
        this.type = type;
    }

    public void setType(String typeString) {
        if (typeString.equals("dataset"))
            this.type = AnnotationType.DATASET;
        else if (typeString.equals("data-acq"))
            this.type = AnnotationType.DATA_ACQ;
        else if (typeString.equals("other"))
            this.type = AnnotationType.OTHER;
        else
            logger.warn("Unexpected annotation type: " + typeString);
    }

    public String getDatasetMention() {
        return this.datasetMention;
    }

    public void setDatasetMention(String datasetMention) {
        this.datasetMention = datasetMention;
    }

    public String getContext() {
        return this.context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public int getPage() {
        return this.page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}

package org.grobid.trainer;

import java.util.*;

import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.OffsetPosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * POJO for dataseer annotation, filled by parsing original dataseer csv dataset, with JSON serialization. 
 *
 * @author Patrice
 */
public class DataseerAnnotation extends Annotation {
    private static final Logger logger = LoggerFactory.getLogger(DataseerAnnotation.class);

    enum AnnotationType {
        DATASET, DATA_ACQ, OTHER;
    }

    // number provided by the CSV row
    private String identifier = null;

    // collection, e.g. PLOS
    private String collectionID = null;

    // identifier (number) of the document
    private String documentId = null;

    // identifier (number) of the dataset in the document
    private String datasetId = null;
    
    // the sentence text as quoted
    private String context = null;

    // page as provided by the original dataset
    private String page = null;

    // sentence
    private String text = null;
    
    // properties
    private String meshDataType = null;
    private String rawDataType = null;
    private String dataType = null;
    private String dataSubType = null;
    private String dataLeafType = null;
    private String dataKeyword = null;
    private String dataAction = null;
    private String acquisitionEquipment = null;
    private String memo = null;
    private String section = null;
    private String subsection = null;

    public String getIdentifier() {
        return this.identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getCollectionID() {
        return this.collectionID;
    }

    public void setCollectionID(String collectionID) {
        this.collectionID = collectionID;
    }

    public String getDocumentId() {
        return this.documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDatasetId() {
        return this.datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMemo() {
        return this.memo;
    }

    public void setMemo(String text) {
        this.memo = memo;
    }

    public String getRawDataType() {
        return this.rawDataType;
    }

    public void setRawDataType(String rawDataType) {
        this.rawDataType = rawDataType;
    }

    public String getDataType() {
        return this.dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataSubType() {
        return this.dataSubType;
    }

    public void setDataSubType(String dataSubType) {
        this.dataSubType = dataSubType;
    }

    public String getDataLeafType() {
        return this.dataLeafType;
    }

    public void setDataLeafType(String dataLeafType) {
        this.dataLeafType = dataLeafType;
    }

    public String getDataKeyword() {
        return this.dataKeyword;
    }

    public void setDataKeyword(String dataKeyword) {
        this.dataKeyword = dataKeyword;
    }

    public String getDataAction() {
        return this.dataAction;
    }

    public void setDataAction(String dataAction) {
        this.dataAction = dataAction;
    }

    public String getAcquisitionEquipment() {
        return this.acquisitionEquipment;
    }

    public void setAcquisitionEquipment(String acquisitionEquipment) {
        this.acquisitionEquipment = acquisitionEquipment;
    }

    public String getSection() {
        return this.section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSubsection() {
        return this.subsection;
    }

    public void setSubsection(String subsection) {
        this.subsection = subsection;
    }

    public String getContext() {
        return this.context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getPage() {
        return this.page;
    }

    public void setPage(String page) {
        this.page = page;
    }

}

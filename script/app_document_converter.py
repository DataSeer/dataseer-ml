'''
Convert annotated document from the DataSeer application in JSON+TEI into csv data 
ready to be added to the standard dataseer classifier training data.  

Usage:

> python3 app_document_converter.py documents.json

documents.json is obtained directly from the mongodb application database via mongoexport:

> mongoexport --collection=documents --db=app --out=documents.json

The script works with gzipped json too.
'''

import os
import sys
import json
import csv
import gzip
import argparse
from lxml import etree

# valid curators for the set of documents annotated by modelcular connections and reviewed by Tim 
# this might need to be adapted in the future
phase1_annotator_identifiers = ['Curator1@molecularconnections.com', 
    'Curator2@molecularconnections.com', 
    'Curator3@molecularconnections.com',
    'Curator4@molecularconnections.com',
    'tim@dataseer.io']

binary_fieldnames = ['doi', 'text', 'datatype']
reuse_fieldnames = ['doi', 'text', 'reuse']
multilevel_fieldnames = ['doi', 'text', 'datatype', 'dataSubtype', 'leafDatatype']

# counter to balance positive and negative examples to be used by the binary classifier
nb_positive_examples = 0
nb_negative_examples = 0

all_datatypes = []

MAX_NEGATIVE_EXAMPLES_FROM_SAME_DOCUMENT = 12

def process_json(json_entry, binary_csv_file, reuse_csv_file, multilevel_csv_file, dataset_section_tei_path):
    global nb_positive_examples
    global nb_negative_examples
    global all_datatypes

    document = json.loads(json_entry)
    document_id = document["_id"]

    # apply filters: document must be validated and modified by at least one listed annotator
    annotators = []
    all_modifiers = document["modifiedBy"]
    if "standard_user" in all_modifiers:
        annotators.append(_get_value(all_modifiers["standard_user"]))
    if "annotator" in all_modifiers:
        annotators.append(_get_value(all_modifiers["annotator"]))
    if "curator" in  all_modifiers:
        annotators.append(_get_value(all_modifiers["curator"]))

    if len(annotators) == 0:
        return

    valid_annotator = False
    for annotator in phase1_annotator_identifiers:
        if annotator in annotators:
            valid_annotator = True
            break

    if not valid_annotator:
        return

    # check status
    status = document["status"]
    if status != 'finish':
        return

    # as identifier we use doi or the _id as fallback
    doi = None
    if "metadata" in document:
        metadata = document["metadata"]
        if "doi" in metadata:
            doi = metadata["doi"]
            if len(doi) == 0:
                doi = None
    if doi is None:
        doi = document_id

    datasets_list = document["datasets"]

    if "extracted" in datasets_list:
        actual_datasets = datasets_list["extracted"]
        for key in actual_datasets.keys():
            dataset = actual_datasets[key]
            text = dataset['text']

            datatype_class = 'dataset'

            # binary classifier data (datatype/no_datatype)
            # doi,text,datatype
            binary_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class})
            nb_positive_examples += 1

            # the reuse information is currently not available

            # reuse classifier (true/false)
            # doi,text,reuse
            #reuse_csv_file.writerow({'doi': doi, 'text': text, 'reuse': reuse_class})

            datatype_class = dataset['dataType']

            if datatype_class not in all_datatypes:
               all_datatypes.append(datatype_class)

            datasub_type_class = dataset['subType']
            leaf_datatype_class = ''

            # multilevel classifier data (datatype, data subtype and leaf datatype)
            multilevel_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class, 'dataSubtype': datasub_type_class, 'leafDatatype': leaf_datatype_class})

    # deleted dataset sentence can be used as negative examples    
    if "deleted" in datasets_list:
        deleted_datasets = datasets_list["deleted"]
        for dataset in deleted_datasets:
            #dataset = actual_datasets[deleted_dataset]
            text = dataset['text']

            datatype_class = 'no_dataset'

            # binary classifier data (datatype/no_datatype)
            # doi,text,datatype
            binary_csv_file.writerow({'doi': doi, 'text': text, 'datatype': datatype_class})
            nb_negative_examples += 1
    

    # save the TEI XML document in the dedicated subdirectory
    document_tei = document["source"]
    destination_tei = os.path.join(dataset_section_tei_path, document_id+".tei.xml")
    with open(destination_tei, "w") as tei_file:
        tei_file.write(document_tei)

    # in addition we can add random sentences to increase the ratio of no_dataset in the binary classifier
    # we can select these negative examples from the actual dataset sections, although reliable negative
    # classifications are also important for selecting the datset section itself

    try:
        root = etree.fromstring(document_tei.encode('utf-8'))
    except:
        print("the parsing of the XML document failed... moving to the next entry...")
        return

    # get random sentence without dataset information and of length at least of 150 characters
    ns = {"tei": "http://www.tei-c.org/ns/1.0"}
    sentences = root.xpath('//tei:s[not(@type)]//text()', namespaces=ns)

    if nb_negative_examples < nb_positive_examples:
        local_addition = 0
        i = 0
        while local_addition < MAX_NEGATIVE_EXAMPLES_FROM_SAME_DOCUMENT and i < len(sentences):
            local_text = sentences[i]
            if len(local_text) > 150:
                # binary classifier data (datatype/no_datatype)
                # doi,text,datatype
                binary_csv_file.writerow({'doi': doi, 'text': local_text, 'datatype': 'no_dataset'})
                nb_negative_examples += 1
                local_addition += 1
            i += 1

def _get_value(json_element):
    # get the first value of a json object, not knowing the key 
    for key in json_element.keys():
        return json_element[key]
    return None

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Converter for annotated document from the DataSeer application in JSON+TEI into training csv and TEI data ")
    parser.add_argument("--document", type=str, help="path to the JSON document file exported from the application mongoDB")
    parser.add_argument("--output", type=str, help="path to a directory where all the training file will be written")
    args = parser.parse_args()

    document_json_file = args.document
    output_path = args.output

    binary_csv = os.path.join(output_path, "binary.csv")
    binary_csv_file = open(binary_csv, mode='w')

    reuse_csv = os.path.join(output_path, "reuse.csv")
    reuse_csv_file = open(reuse_csv, mode='w')

    multilevel_csv = os.path.join(output_path, "multilevel.csv")
    multilevel_csv_file = open(multilevel_csv, mode='w')

    dataset_section_tei_path = os.path.join(output_path, "corpus")

    # dataset_section_tei_path already exist we delete it
    if os.path.exists(dataset_section_tei_path) and os.path.isdir(dataset_section_tei_path):
        try:
            os.rmdir(dataset_section_tei_path)
        except OSError:
            print ("Deletion of the existing directory %s failed" % dataset_section_tei_path)
        else:
            print ("Successfully deleted the existing directory %s" % dataset_section_tei_path)

    # create directory dataset_section_tei_path
    try:
        os.mkdir(dataset_section_tei_path)
    except OSError:
        print ("Creation of the directory %s failed" % dataset_section_tei_path)
    else:
        print ("Successfully created the directory %s " % dataset_section_tei_path)

    # init the csv file writers so that we can then append the extra data
    binary_csv_writer = csv.DictWriter(binary_csv_file, fieldnames=binary_fieldnames)
    binary_csv_writer.writeheader()

    reuse_csv_writer = csv.DictWriter(reuse_csv_file, fieldnames=reuse_fieldnames)
    reuse_csv_writer.writeheader()

    multilevel_csv_writer = csv.DictWriter(multilevel_csv_file, fieldnames=multilevel_fieldnames)
    multilevel_csv_writer.writeheader()

    if document_json_file.endswith(".gz"):
        with gzip.open(document_json_file,'rt') as f:
            for line in f:
                process_json(line, binary_csv_writer, reuse_csv_writer, multilevel_csv_writer, dataset_section_tei_path)
    else:
        with open(document_json_file) as fp: 
            for line in fp.readlines():
                process_json(line, binary_csv_writer, reuse_csv_writer, multilevel_csv_writer, dataset_section_tei_path)

    binary_csv_file.close()
    reuse_csv_file.close()
    multilevel_csv_file.close()

    print("for the binary classifier: nb_positive_examples", nb_positive_examples, "/ nb_negative_examples", nb_negative_examples)

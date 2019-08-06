Work in progress !

# Build

Install GROBID:

> git clone https://github.com/kermitt2/grobid

Install dataseer-ml and copy it as a sub-module of GROBID:

> git clone https://github.com/kermitt2/dataseer-ml

> cp -rf dataseer-ml grobid/

Build:

> cd grobid/dataseer-ml

> ./gradlew clean install

# Web service API

## Start the service

> ./gradlew appRun

## Process a sentence

Identify if the sentence introduces a dataset, if yes classify the dataset type.

Example: 

> curl -X POST -d "text=This is a sentence." http://localhost:8060/service/processDataseerSentence

> curl -GET --data-urlencode "text=This is a another sentence." http://localhost:8080/service/processDataseerSentence


## Process a TEI document

Upload a TEI document, identify dataset introductory section, segment into sentences, identify sentence introducing a dataset and classify the dataset type. Return the TEI document enriched with Dataseer information.

Example:

> curl --form input=@./resources/samples/journal.pone.0198050.tei.xml localhost:8060/service/processDataseerTEI


## Process a PDF document

Upload a PDF document, extract its content and convert it into structured TEI (via GROBID), identify dataset introductory section, segment into sentences, identify sentence introducing a dataset and classify the dataset type. Return a TEI representation of the PDF, enriched with Dataseer information.

Example:

> curl --form input=@./resources/samples/journal.pone.0198050.pdf localhost:8060/service/processDataseerPDF


# Training data assembling and generating classification models

## Importing training data

Training data is available in a tabular format with reference to Open Access articles. The following process will align these tabular data with the actual article content (JATS and/or PDF via GROBID) to create a full training corpus. 

> ./gradlew annotated_corpus_generator_csv -Ppdf=PATH/TO/THE/FULL/TEXTS -Pcsv=PATH/TO/THE/TABULAR/TRAINING/DATA -Pxml=PATH/WHERE/TO/WRITE/THE/ASSEMBLED/TRAINING/DATA

For instance:

> ./gradlew annotated_corpus_generator_csv -Ppdf=/mnt/data/resources/plos/0/pdf/ -Pcsv=resources/dataset/dataseer/csv/ -Pxml=resources/dataset/dataseer/corpus/

Some reports will be generated to describe the alignment failures. 

## Training the classification models

After assembling the training data, the classification models can be trained with the following command:

> 



... 


# Additional convenient scripts

## Load training data documents in the dataseer web application

All the documents present in the local training data repository (after importing the training, see above) under `dataseer-ml/resources/dataset/dataseer/corpus/` will be loaded via the dataseer web API. 

> cd scripts/

> node loader.js


## Convert data type information from csv to json

> cd scripts/

> pip3 install pandas

> python3 converter.py ../resources/DataTypes.csv ../resources/DataTypes.json



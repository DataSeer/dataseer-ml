**dataseer-ml** is a GROBID module able to identify implicit mentions of datasets in a scientific article and to clasify the identified dataset in a hierarchy of dataset types, these data types being directly derived from MeSH.

The goal of this process is to further drive the authors of the article to the best research data sharing practices, i.e. to ensure that the dataset is associated with data availability statement, permanent identifiers and in general requirements regarding Open Science and reproducibility. This further process is realized by the dataseer web application which includes a GUI to be used by the authors, suggesting data sharing policies based on the predicted data types for each identified dataset.  

The module can process a variety of scientific article formats, including mainstream publisher's native XML submission formats: PDF, TEI, JATS/NLM, ScholarOne, BMJ, Elsevier staging format, OUP, PNAS, RSC, Sage, Wiley, etc.

`.docx` format is also supported in a specific branch, but not yet merged. 

Work in progress !

# Approach

## Dataset identification and classification

The processing of an article follows 5 steps: 

1. Given an article to be processed by DataSeer, the document is first parsed and structured automatically by [Grobid](https://github.com/kermitt2/grobid). This includes metadata extraction and consolidation against CrossRef and PubMed, structuring the text body and bibliographical references. 

2. The document body is then segmented into sentences thanks to OpenNLP with some customization to better support scientific texts (i.e. avoiding wrong sentence break in the middle of reference callout or in the middle of scientific notations). 

3. Each sentence is going through a cascade of text classifiers, all based on a fine-tuned SciBERT deep learning architecture, to predict if the sentence introduce a dataset, and if yes, which dataset type and sub type is introduced. 

4. The text body is then processed by a sequence labeling model which aims at recognizing the section relevant to dataset introductions and presentations. "Materials and Methods" for instance is a usual relevant section, but other sections might be relevant and the "Materials and Methods" sections can appeared with a variety of section headers. This sequence labelling process is realized by CRF using various features including the predictions produced in the previous steps 3.

5. A final selection of the predicted datasets takes place for the sections identified as introducing potentially datasets, using the result of the sentence classification of step 3 for predicting additionally the type and subtype of the recognized datasets. 

The result of the service is a TEI file representing the article, enriched with sentence boundaries and predicted data set information. 

## Training

The DataSeer dataset is a manual collection of all dataset introduction contexts for 2000 Open Access articles from PLOS, classified into the taxonomy of data types developed at the Dataseer [ResearchDataWiki](http://wiki.dataseer.io/doku.php). It contains 21,041 manually classified/annotated sentences about datasets.  

# Build

Install GROBID:

> git clone https://github.com/kermitt2/grobid

Install then *dataseer-ml* and move it as a sub-module of GROBID:

> git clone https://github.com/kermitt2/dataseer-ml

> mv dataseer-ml grobid/

Install DeLFT:

> git clone https://github.com/kermitt2/delft

Follow the installation described in the [DeLFT documentation](https://github.com/kermitt2/delft#install). If necessary, update the path to the DeLFT installation in the `grobid.properties` file located under `grobid-home/config/grobid.properties`.

By default, the project can process scientific articles in PDF and TEI formats. To process JATS/NLM, scholarOne and a variety of other native publisher formats, Pub2TEI needs to be installed: 

> git clone https://github.com/kermitt2/Pub2TEI

If required, update the path to the Pub2TEI installation in the `dataseer-ml.properties` file located under `dataseer-ml/src/main/resources/`:

```
# path to Pub2TEI repository as available at https://github.com/kermitt2/Pub2TEI
grobid.dataseer.pub2tei.path=../../Pub2TEI/
```

Build dataseer-ml:

> cd grobid/dataseer-ml

> ./gradlew clean install


# Web service API

## Start the service

> ./gradlew appRun

## Service console

Javascript demo/console web app is then accessible at ```http://localhost:8060```. From the console and the `RESTfull services` tab, you can process chunk of text (select `Process text Sentence`), process a complete PDF document (select `Process PDF`), process a TEI document (select `Process TEI`) or process an XML publisher native document (such as JATS - select `Process JATS/NLM/...`) .

![DataSeer-ml Demo](doc/images/screen01.png)

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

## Process native publisher XML document

Upload a publisher native XML format document, convert it into structured TEI (via Pub2TEI), identify dataset introductory section, segment into sentences, identify sentence introducing a dataset and classify the dataset type. Return a TEI representation of the PDF, enriched with Dataseer information.

Example:

> curl --form input=@./resources/samples/journal.pone.0198050.xml localhost:8060/service/processDataseerJATS

See [Pub2TEI](https://github.com/kermitt2/Pub2TEI) for the exact list of supported formats.

# Training data assembling and generating classification models

## Importing training data

Training data is available in a tabular format with reference to Open Access articles. The following process will align these tabular data with the actual article content (JATS and/or PDF via GROBID) to create a full training corpus. 

> ./gradlew annotated_corpus_generator_csv -Ppdf=PATH/TO/THE/FULL/TEXTS -Pcsv=PATH/TO/THE/TABULAR/TRAINING/DATA -Pxml=PATH/WHERE/TO/WRITE/THE/ASSEMBLED/TRAINING/DATA

For instance:

> ./gradlew annotated_corpus_generator_csv -Ppdf=/mnt/data/resources/plos/0/pdf/ -Pcsv=resources/dataset/dataseer/csv/ -Pxml=resources/dataset/dataseer/corpus/

Some reports will be generated to describe the alignment failures. 

## Training the classification models

The classifier models are relying on the [DeLFT](https://github.com/kermitt2/delft) deep learning library, which is integrated in Grobid. 

After assembling the training data, the classification models can be trained with the following command:

> 


[To Be Completed]


# Additional convenient scripts

## Load training data documents in the dataseer web application

All the documents present in the local training data repository (after importing the training, see above) under `dataseer-ml/resources/dataset/dataseer/corpus/` will be loaded via the dataseer web API. 

> cd scripts/

> node loader.js


## Convert data type information from csv to json

> cd scripts/

> pip3 install pandas

> python3 converter.py ../resources/DataTypes.csv ../resources/DataTypes.json


## Contact and License

Author and contact: Patrice Lopez (<patrice.lopez@science-miner.com>)

The development of dataseer-ml is supported by a [Sloan Foundation](https://sloan.org/) grant, see [here](https://coko.foundation/coko-receives-sloan-foundation-grant-to-build-dataseer-a-missing-piece-in-the-data-sharing-puzzle/)

DataSeer-ml is distributed under [MIT license](https://opensource.org/licenses/MIT), copyright Aspiration

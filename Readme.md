Work in progress !

# Build

> git clone https://github.com/kermitt2/dataseer-ml

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


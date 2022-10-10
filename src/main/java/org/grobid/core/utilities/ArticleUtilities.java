package org.grobid.core.utilities;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.fasterxml.jackson.databind.*;

import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.regex.*;
import java.net.URL;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import java.net.URLDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grobid.core.utilities.KeyGen;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.engines.DataseerClassifier;

import org.apache.commons.io.FileUtils;

/**
 *  Some convenient methods for retrieving the original PDF files from the annotated set.
 */
public class ArticleUtilities {

    private static final Logger logger = LoggerFactory.getLogger(ArticleUtilities.class);

    private DataseerConfiguration dataseerConfiguration;

    private static String halURL = "https://hal.archives-ouvertes.fr";
    private static String pmcURL = "http://www.ncbi.nlm.nih.gov/pmc/articles";
    private static String arxivURL = "https://arxiv.org/pdf";

    public int totalDOIFail = 0;
    public int totalFail = 0;

    public enum Source {
        HAL, PMC, ARXIV, DOI;
    }

    /**
     *  Get the PDF file from an article ID.
     *  If the source is not present, we try to guess it from the identifier itself.
     *
     *  Return null if the identification fails.
     */
    public File getPDFDoc(String identifier, Source source) {
        try {
            if (source == null) {
                source = guessDomain(identifier);
            }

            if (source == null) {
                totalFail++;
                logger.info("Cannot identify download url for " + identifier);
                return null;
            }

            String urll = null;
            switch (source) {
                case HAL:
                    urll = halURL+File.separator+identifier+"/document";
                    break;
                case PMC:
                    urll = pmcURL+File.separator+identifier+"/pdf";
                    break;
                case ARXIV:
                    String localNumber = identifier.replace("arXiv:", "");
                    urll = arxivURL+File.separator+localNumber+".pdf";
                    break;
                case DOI:
                    // hard case to find the right PDF, we use the Unpaywall API to get the best Open Access PDF url
                    // handle boring URL encoding
                    try {
                        identifier = urlDecode(identifier);
                        urll = getUnpaywallOAUrl(identifier);
                        if (urll == null || urll.equals("null")) {
                            urll = getGluttonOAUrl(identifier);
                        }
                        if (urll == null || urll.equals("null")) {
                            totalDOIFail++;
                            logger.warn("No Open Access PDF found via Unpaywall for DOI: " + identifier);
                            System.out.println("No Open Access PDF found via Unpaywall for DOI: " + identifier);
                            urll = null;
                        }
                    } catch(UnsupportedEncodingException e) {
                        logger.warn("Invalid DOI identifier encoding: " + identifier, e);
                        System.out.println("Invalid DOI: " + identifier);
                    } catch(Exception e) {
                        logger.warn("No Open Access PDF found for DOI: " + identifier, e);
                        System.out.println("No Open Access PDF found via Unpaywall for DOI: " + identifier);
                    }
            }

            if (urll == null) {
                totalFail++;
                logger.info("Cannot identify download url for " + identifier);
                return null;
            }

            DataseerConfiguration dataseerConfiguration = DataseerClassifier.getInstance().getDataseerConfiguration();
            File file = uploadFile(urll, 
                dataseerConfiguration.getTmpPath(), 
                KeyGen.getKey()+".pdf");
            return file;
        }
        catch (Exception e) { 
            e.printStackTrace(); 
        }

        return null;
    }
    
    public File getPDFDoc(String identifier) {
        return getPDFDoc(identifier, null);
    }

    private static String urlDecode(String value) throws Exception {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
    }

    private Source guessDomain(String identifier) {
        identifier = identifier.replace("%2F", "/");
        if (identifier.startsWith("PMC")) {
            return Source.PMC;
        } else if (identifier.startsWith("hal-")) {
            return Source.HAL;
        } else if (identifier.startsWith("10.") || 
                   identifier.startsWith("https://doi.org/10.") || 
                   identifier.startsWith("http://dx.doi.org/10.")) {
            return Source.DOI;
        } else {
            Matcher arXivMatcher = TextUtilities.arXivPattern.matcher(identifier);
            if (arXivMatcher.find()) {  
                return Source.ARXIV;
            }
        }
        return null;
    }

    private static String getUnpaywallOAUrl(String doi) throws Exception {
        doi = doi.trim();
        doi = doi.replace(" ", "");
        
        String queryUrl = "https://api.unpaywall.org/v2/" + doi + "?email=patrice.lopez@science-miner.com";
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(queryUrl);

        // add request header
        //request.addHeader("User-Agent", USER_AGENT);

        HttpResponse response = client.execute(request);

        System.out.println("\nSending 'GET' request to URL : " + queryUrl);
        System.out.println("Response Code : " + 
                       response.getStatusLine().getStatusCode());

        BufferedReader rd = new BufferedReader(
                       new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        String json = result.toString();
        //System.out.println(result.toString());

        // get the best oa url if it exists
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(json);
        // json path is best_oa_location / url_for_pdf
        JsonNode bestOALocation = jsonNode.path("best_oa_location");
        String urlForPdf = null;
        if (!bestOALocation.isMissingNode()) {
            JsonNode urlForPdfNode = bestOALocation.path("url_for_pdf");
            if (!urlForPdfNode.isMissingNode()) {
                urlForPdf = urlForPdfNode.asText();
            }
        }
        return urlForPdf;
    }

    private static String getGluttonOAUrl(String doi)  throws Exception {
        DataseerConfiguration dataseerConfiguration = DataseerClassifier.getInstance().getDataseerConfiguration();
        String host = dataseerConfiguration.getGluttonHost();
        String port = dataseerConfiguration.getGluttonPort();
        String queryUrl = "http://" + host;
        if (port != null)
            queryUrl += ":" + port;
        queryUrl += "/service/oa?doi="+doi;
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(queryUrl);

        HttpResponse response = client.execute(request);

        System.out.println("\nSending 'GET' request to URL : " + queryUrl);
        System.out.println("Response Code : " + 
                       response.getStatusLine().getStatusCode());

        BufferedReader rd = new BufferedReader(
                       new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        String json = result.toString();

        // get the best oa url if it exists
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(json);
        // json path is best_oa_location / url_for_pdf
        JsonNode urlForPdfNode = jsonNode.path("oaLink");
        String urlForPdf = null;
        if (!urlForPdfNode.isMissingNode()) {
            urlForPdf = urlForPdfNode.asText();
        }
        return urlForPdf;
    }

    private static File uploadFile(String urll, String path, String name) throws Exception {
        try {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                System.out.println("temporary path for dataseer invalid: " + path);
                return null;
            }

            System.out.println("GET: " + urll);
            URL url = new URL(urll);

            File outFile = new File(path, name);

            Downloader downloader = new Downloader();
            downloader.download(url, outFile);
            //downloader.downloadExternal(url, outFile);
            return outFile;
        } 
        catch (Exception e) {
            throw new Exception("An exception occured while downloading " + urll, e);
        }
    }

    /**
     * Apply a Pub2TEI transformation to an XML file to produce a TEI file.
     * Input XML file must be a native XML publisher file supported by Pub2TEI.
     * Output the path to the transformed outputed file or null if the transformation failed.
     */
    public static String applyPub2TEI(String inputFilePath, String outputFilePath, String pathToPub2TEI) {
        // we use an external command line for simplification (though it would be more elegant to 
        // stay in the current VM)
        // java -jar Samples/saxon9he.jar -s:/mnt/data/resources/plos/0/ -xsl:Stylesheets/Publishers.xsl -o:/mnt/data/resources/plos/0/tei/ -dtd:off -a:off -expand:off -t

        // remove first the DTD declaration from the input nlm/jats XML because all these shitty xml mechanisms break 
        // the process at one point or another or keep looking for something over the internet 
        try {
            String xmlContent = FileUtils.readFileToString(new File(inputFilePath), "UTF-8");
            xmlContent = xmlContent.replaceAll("<!DOCTYPE((.|\n|\r)*?)\">", ""); 
            FileUtils.writeStringToFile(new File(inputFilePath), xmlContent, "UTF-8");
        } catch(IOException e) {
            logger.error("Fail to preprocess the XML file to be transformed", e);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(); 
        String s = "-s:"+inputFilePath;
        File dirToPub2TEI = new File(pathToPub2TEI);

        String xsl = "-xsl:" + dirToPub2TEI.getAbsolutePath() + "/Stylesheets/Publishers.xsl";
        String o = "-o:"+outputFilePath;
        processBuilder.command("java", "-jar", dirToPub2TEI.getAbsolutePath() + "/Samples/saxon9he.jar", s, xsl, o, "-dtd:off", "-a:off", "-expand:off", "-t");
        //processBuilder.directory(new File(pathToPub2TEI)); 
        //System.out.println(processBuilder.command().toString());
        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                System.out.println("XML transformation done");
            } else {
                // abnormal...
                System.out.println("XML transformation failed");
                outputFilePath = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            outputFilePath = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            outputFilePath = null;
        }
        return outputFilePath;
    }

    /**
     * Write an input stream in temp directory.
     */
    public static File writeInputFile(InputStream inputStream, String extension) {
        logger.debug(">> set origin document for stateless service'...");

        File originFile = null;
        OutputStream out = null;
        try {
            originFile = IOUtilities.newTempFile("origin", extension);

            out = new FileOutputStream(originFile);

            byte buf[] = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            logger.error(
                    "An internal error occurs, while writing to disk (file to write '"
                            + originFile + "').", e);
            originFile = null;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                inputStream.close();
            } catch (IOException e) {
                logger.error("An internal error occurs, while writing to disk (file to write '"
                        + originFile + "').", e);
                originFile = null;
            }
        }
        return originFile;
    }

}
package org.grobid.service;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.grobid.core.lexicon.DataseerLexicon;
import org.grobid.core.main.GrobidHomeFinder;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.DataseerProperties;
import org.grobid.core.utilities.GrobidProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Arrays;

/**
 * RESTful service for GROBID dataseer extension.
 *
 * @author Patrice
 */
@Singleton
@Path(DataseerPaths.PATH_DATASEER)
public class DataseerRestService implements DataseerPaths {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataseerRestService.class);

    private static final String TEXT = "text";
    private static final String XML = "xml";
    private static final String TEI = "tei";
    private static final String PDF = "pdf";
    private static final String INPUT = "input";

    public DataseerRestService() {
        LOGGER.info("Init Servlet DataseerRestService.");
        LOGGER.info("Init lexicon and KB resources.");
        try {
            String pGrobidHome = DataseerProperties.get("grobid.home");

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            LOGGER.info(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());

            //LibraryLoader.load();
            DataseerLexicon.getInstance();
        } catch (final Exception exp) {
            LOGGER.error("GROBID dataseer initialisation failed. ", exp);
        }

        LOGGER.info("Init of Servlet DataseerRestService finished.");
    }

    @Path(PATH_DATASEER_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processText_post(@FormParam(TEXT) String text) {
        LOGGER.info(text);
        return DataseerProcessString.processSentence(text);
    }

    @Path(PATH_DATASEER_SENTENCE)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @GET
    public Response processText_get(@QueryParam(TEXT) String text) {
        LOGGER.info(text);
        return DataseerProcessString.processSentence(text);
    }
    
    @Path(PATH_DATASEER_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processPDF(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processPDF(inputStream);
    }

    @Path(PATH_DATASEER_TEI)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processTEI(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processTEI(inputStream);
    }

    @Path(PATH_DATASEER_JATS)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_XML)
    @POST
    public Response processJATS(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processJATS(inputStream);
    }

    @Path(PATH_ANNOTATE_DATASEER_PDF)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    @POST
    public Response processPDFAnnotation(@FormDataParam(INPUT) InputStream inputStream) {
        return DataseerProcessFile.processPDFAnnotation(inputStream);
    }
}

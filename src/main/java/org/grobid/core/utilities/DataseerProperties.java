package org.grobid.core.utilities;

import java.io.IOException;
import java.io.InputStream;

public class DataseerProperties {

	public static String get(String key) {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		InputStream stream = classLoader.getResourceAsStream("grobid-dataseer.properties");
		
		java.util.Properties properties = new java.util.Properties();
		try {
			properties.load(stream);
		} catch (IOException e1) {
			return null;
		}
		return properties.getProperty(key);
	}

	public static String getTmpPath() {
		return DataseerProperties.get("grobid.dataseer.tmpPath");
	}
	
	public static String getEmbeddings() {
		return DataseerProperties.get("grobid.dataseer.engine.embeddings");
	}

	/**
	 *  This will override engine selection in GROBID
	 */
	public static String getEngine() {
		return DataseerProperties.get("grobid.dataseer.engine");
	}
}

package niagara.ndom;

import java.io.IOException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * <code>XML4JParser</code> is a wrapper for com.ibm.xml.parsers.DOMParser
 * 
 * @author <a href="mailto:vpapad@cse.ogi.edu">Vassilis Papadimos</a>
 */

public class XML4JParser implements niagara.ndom.DOMParser {
	private com.ibm.xml.parsers.DOMParser parser;
	private SimpleXML4JHandler sh;

	public XML4JParser() {
		parser = new com.ibm.xml.parsers.DOMParser();
		sh = new SimpleXML4JHandler();
	}

	public void parse(InputSource is) throws SAXException, IOException {
		sh.reset();
		parser.setErrorHandler(sh);
		parser.parse(is);
	}

	public Document getDocument() {
		return parser.getDocument();
	}

	public boolean hasErrors() {
		return (sh.hasErrors());
	}

	public boolean hasWarnings() {
		return (sh.hasWarnings());
	}

	public boolean supportsStreaming() {
		return false;
	}

	/*
	 * @see niagara.ndom.DOMParser#getErrorStrings()
	 */
	public String getErrorStrings() {
		return null;
	}

	/*
	 * @see niagara.ndom.DOMParser#getWarningStrings()
	 */
	public String getWarningStrings() {
		return null;
	}
}

class SimpleXML4JHandler implements ErrorHandler {
	private boolean hasErrors, hasWarnings;

	public SimpleXML4JHandler() {
		reset();
	}

	public boolean hasWarnings() {
		return hasWarnings;
	}

	public boolean hasErrors() {
		return hasErrors;
	}

	public void reset() {
		hasErrors = hasWarnings = false;
	}

	public void error(SAXParseException e) {
		hasErrors = true;
	}

	public void fatalError(SAXParseException e) {
		hasErrors = true;
	}

	public void warning(SAXParseException e) {
		hasWarnings = true;
	}

}

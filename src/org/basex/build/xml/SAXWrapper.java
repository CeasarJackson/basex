package org.basex.build.xml;

import static org.basex.Text.*;
import static org.basex.util.Token.*;
import java.io.IOException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.basex.BaseX;
import org.basex.build.Builder;
import org.basex.build.Parser;
import org.basex.core.ProgressException;
import org.basex.io.IO;
import org.basex.util.Token;
import org.basex.util.TokenBuilder;
import org.basex.util.TokenList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class parses an XML document with Java's default SAX parser.
 * Note that large file cannot be parsed with the default parser due to
 * entity handling (e.g. the DBLP data).
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class SAXWrapper extends Parser {
  /** Element counter. */
  int nodes;
  /** DTD flag. */
  boolean dtd;
  /** Builder reference. */
  Builder builder;
  /** Optional XML reader. */
  SAXSource source;

  // needed for XMLEntityManager: increase entity limit
  static { System.setProperty("entityExpansionLimit", "536870912"); }

  /**
   * Constructor.
   * @param s sax source
   */
  public SAXWrapper(final SAXSource s) {
    super(IO.get(s.getSystemId()));
    source = s;
  }

  @Override
  public void parse(final Builder build) throws IOException {
    builder = build;

    try {
      XMLReader r = source != null ? source.getXMLReader() : null;
      if(r == null) {
        final SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        f.setValidating(false);
        r = f.newSAXParser().getXMLReader();
      }
      final SAXParser p = new SAXParser();
      r.setDTDHandler(p);
      r.setContentHandler(p);
      r.setProperty("http://xml.org/sax/properties/lexical-handler", p);
      r.setErrorHandler(p);

      builder.startDoc(token(io.name()));
      final InputSource is = source.getInputSource();
      if(is != null) r.parse(is);
      else r.parse(source.getSystemId());
      builder.endDoc();

    } catch(final SAXParseException ex) {
      final String msg = BaseX.info(SCANPOS, ex.getSystemId(),
          ex.getLineNumber(), ex.getColumnNumber()) + ": " + ex.getMessage();
      final IOException ioe = new IOException(msg);
      ioe.setStackTrace(ex.getStackTrace());
      throw ioe;
    } catch(final ProgressException ex) {
      throw ex;
    } catch(final Exception ex) {
      final IOException ioe = new IOException(ex.getMessage());
      ioe.setStackTrace(ex.getStackTrace());
      throw ioe;
    }
  }

  @Override
  public String head() {
    return PROGCREATE;
  }

  @Override
  public String det() {
    return BaseX.info(NODESPARSED, io.name(), nodes);
  }

  @Override
  public double percent() {
    return (nodes / 1000000d) % 1;
  }

  /** SAX Parser. */
  class SAXParser extends DefaultHandler implements LexicalHandler {
    @Override
    public void startElement(final String uri, final String ln, final String qn,
        final Attributes at) throws SAXException {

      try {
        finishText();
        final int as = at.getLength();
        atts.reset();
        for(int a = 0; a < as; a++) {
          atts.add(Token.token(at.getQName(a)), Token.token(at.getValue(a)));
        }
        builder.startElem(Token.token(qn), atts);
        nodes++;
      } catch(final IOException ex) {
        error(ex);
      }
    }

    @Override
    public void endElement(final String uri, final String ln, final String qn)
        throws SAXException {

      try {
        finishText();
        builder.endElem(Token.token(qn));
      } catch(final IOException ex) {
        error(ex);
      }
    }

    @Override
    public void characters(final char[] ch, final int s, final int l) {
      final int e = s + l;
      for(int i = s; i < e; i++) tb.addUTF(ch[i]);
    }

    @Override
    public void processingInstruction(final String name, final String cont)
        throws SAXException {

      if(dtd) return;
      try {
        finishText();
        builder.pi(new TokenBuilder(name + ' ' + cont));
        nodes++;
      } catch(final IOException ex) {
        error(ex);
      }
    }

    /** {@inheritDoc} */
    public void comment(final char[] ch, final int s, final int l)
        throws SAXException {

      if(dtd) return;
      try {
        finishText();
        builder.comment(new TokenBuilder(new String(ch, s, l)));
        nodes++;
      } catch(final IOException ex) {
        error(ex);
      }
    }

    /** Temporary string builder. */
    private final TokenBuilder tb = new TokenBuilder();
    /** Temporary namespace prefix. */
    private final TokenList np = new TokenList();
    /** Temporary namespace value. */
    private final TokenList nv = new TokenList();

    /**
     * Checks if a text node has to be written.
     * @throws IOException I/O exception
     */
    private void finishText() throws IOException {
      if(tb.size != 0) {
        builder.text(tb, false);
        tb.reset();
        nodes++;
      }
      for(int i = 0; i < np.size; i++) builder.startNS(np.list[i], nv.list[i]);
      np.reset();
      nv.reset();
    }

    /**
     * Creates and throws a SAX exception for the specified exception.
     * @param ex exception
     * @throws SAXException SAX exception
     */
    private void error(final IOException ex) throws SAXException {
      final SAXException ioe = new SAXException(ex.getMessage());
      ioe.setStackTrace(ex.getStackTrace());
      throw ioe;
    }

    // Entity Resolver
    /* public InputSource resolveEntity(String pub, String sys) { } */

    // DTDHandler
    /* public void notationDecl(String name, String pub, String sys) { } */
    /* public void unparsedEntityDecl(final String name, final String pub,
        final String sys, final String not) { } */

    // ContentHandler
    /*public void setDocumentLocator(final Locator locator) { } */
    /*public void startDocument() { } */
    /*public void endDocument() { } */

    @Override
    public void startPrefixMapping(final String prefix, final String uri) {
      np.add(Token.token(prefix));
      nv.add(Token.token(uri));
    }

    /*public void endPrefixMapping(final String prefix) { } */
    /*public void ignorableWhitespace(char[] ch, int s, int l) { } */
    /*public void skippedEntity(final String name) { } */

    // ErrorHandler
    /* public void warning(final SAXParseException e) { } */
    /* public void fatalError(final SAXParseException e) { } */

    // LexicalHandler
    public void startDTD(final String n, final String pid, final String sid) {
      dtd = true;
    }

    public void endDTD() {
      dtd = false;
    }

    public void endCDATA() { }
    public void endEntity(final String name) { }
    public void startCDATA() { }
    public void startEntity(final String name) { }
  }
}

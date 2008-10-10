package org.basex.build.xml;

import static org.basex.Text.*;
import static org.basex.util.Token.*;
import java.io.IOException;
import java.util.Stack;
import org.basex.BaseX;
import org.basex.build.Builder;
import org.basex.build.Parser;
import org.basex.io.IO;
import org.basex.util.Token;
import org.basex.util.TokenBuilder;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

/**
 * This class parses an XML document via a conventional SAX parser.
 * Would be the easiest solution, but some large file cannot be parsed
 * with the default parser.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class DOCWrapper extends Parser {
  /** Name of the document. */
  private final String filename;
  /** Root document. */
  private final Node doc;
  /** Element counter. */
  private int nodes;

  /**
   * Constructor.
   * @param d document instance
   * @param fn filename
   */
  public DOCWrapper(final Document d, final String fn) {
    super(IO.DUMMY);
    doc = d;
    filename = fn;
  }

  @Override
  public void parse(final Builder builder) throws IOException {
    builder.startDoc(token(filename));

    final Stack<NodeIterator> stack = new Stack<NodeIterator>();
    stack.push(new NodeIterator(doc));

    while(!stack.empty()) {
      final NodeIterator ni = stack.peek();
      if(ni.more()) {
        final Node n = ni.curr();
        if(n instanceof Element) {
          stack.push(new NodeIterator(n));

          final NamedNodeMap at = n.getAttributes();
          final int as = at.getLength();
          atts.reset();
          for(int a = 0; a < as; a++) {
            atts.add(Token.token(at.item(a).getNodeName()),
                Token.token(at.item(a).getNodeValue()));
          }
          builder.startElem(Token.token(n.getNodeName()), atts);
        } else if(n instanceof Text) {
          builder.text(new TokenBuilder(n.getNodeValue()), false);
        } else if(n instanceof Comment) {
          builder.comment(new TokenBuilder(n.getNodeValue()));
        } else if(n instanceof ProcessingInstruction) {
          builder.pi(new TokenBuilder(n.getNodeName() + ' ' +
              n.getNodeValue()));
          builder.text(new TokenBuilder(n.getNodeValue()), false);
        }
        nodes++;
      } else {
        stack.pop();
        if(stack.empty()) break;
        builder.endElem(Token.token(stack.peek().curr().getNodeName()));
      }
    }
    builder.endDoc();
  }

  @Override
  public String head() {
    return PROGCREATE;
  }

  @Override
  public String det() {
    return BaseX.info(NODESPARSED, filename, nodes);
  }

  @Override
  public double percent() {
    return (nodes / 1000000d) % 1;
  }

  /** Node iterator. */
  private static final class NodeIterator {
    /** Node list. */
    private final NodeList nl;
    /** Position. */
    private int i = -1;

    /**
     * Constructor.
     * @param n input node
     */
    NodeIterator(final Node n) {
      nl = n.getChildNodes();
    }

    /**
     * Checks if more nodes are found.
     * @return result of check
     */
    boolean more() {
      return ++i < nl.getLength();
    }
    /**
     * Returns the current node.
     * @return current node
     */
    Node curr() {
      return nl.item(i);
    }
  }
}

/* Materialization of DOM Tree
  final TransformerFactory tf = TransformerFactory.newInstance();
  final Transformer tr = tf.newTransformer();
  final CachedOutput out = new CachedOutput();
  tr.transform(new DOMSource((Document) o), new StreamResult(out));
  final IOContent cont = new IOContent(out.finish());
  return new DNode(CreateDB.xml(cont, "tmp"), 0);
*/

package org.basex.http.restxq;

import static org.basex.http.web.WebText.*;
import static org.basex.util.Token.*;

import java.io.*;

import javax.servlet.*;

import org.basex.http.*;
import org.basex.http.web.*;
import org.basex.io.out.*;
import org.basex.io.serial.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.util.http.*;

/**
 * This class creates a new HTTP response.
 *
 * @author BaseX Team 2005-19, BSD License
 * @author Christian Gruen
 */
public final class RestXqResponse extends WebResponse {
  /** HTTP connection. */
  private final HTTPConnection conn;

  /** Function. */
  private RestXqFunction func;
  /** Status message. */
  private String message;
  /** Status code. */
  private Integer status;

  /**
   * Constructor.
   * @param conn HTTP connection
   */
  public RestXqResponse(final HTTPConnection conn) {
    super(conn.context);
    this.conn = conn;
  }

  @Override
  protected void init(final WebFunction function) throws QueryException, IOException {
    func = new RestXqFunction(function.function, qc, function.module);
    qc.putProperty(HTTPText.REQUEST, conn.req);
    qc.jc().type(RESTXQ);
    func.parse(ctx);
  }

  @Override
  protected void bind(final Expr[] args, final Object data) throws QueryException, IOException {
    func.bind(args, data, conn, qc);
  }

  @Override
  public boolean serialize() throws QueryException, IOException, ServletException {
    final String id = func.singleton;
    final RestXqSingleton singleton = id != null ? new RestXqSingleton(conn, id, qc) : null;
    String redirect = null, forward = null;
    OutputStream out = null;
    boolean response;

    qc.register(ctx);
    try {
      // evaluate query
      final Iter iter = qc.iter();
      Item item = iter.next();
      response = item != null;

      SerializerOptions so = func.output;
      boolean head = true;

      // handle special cases
      if(item != null && item.type == NodeType.ELM) {
        final ANode node = (ANode) item;
        if(REST_REDIRECT.eq(node)) {
          // send redirect to browser
          final ANode ch = node.children().next();
          if(ch == null || ch.type != NodeType.TXT) throw func.error(NO_VALUE_X, node.name());
          redirect = string(ch.string()).trim();
          item = null;
        } else if(REST_FORWARD.eq(node)) {
          // server-side forwarding
          final ANode ch = node.children().next();
          if(ch == null || ch.type != NodeType.TXT) throw func.error(NO_VALUE_X, node.name());
          forward = string(ch.string()).trim();
          item = null;
        } else if(REST_RESPONSE.eq(node)) {
          // custom response
          so = build(node);
          item = iter.next();
          head = item != null;
        }
      }
      if(head && func.methods.size() == 1 && func.methods.contains(HttpMethod.HEAD.name()))
        throw func.error(HEAD_METHOD);

      // initialize serializer
      conn.sopts(so);
      conn.initResponse();
      if(status != null) conn.status(status, message);

      // serialize result
      if(item != null) {
        out = id != null ? new ArrayOutput() : conn.res.getOutputStream();
        try(Serializer ser = Serializer.get(out, so)) {
          for(; item != null; item = qc.next(iter)) ser.serialize(item);
        }
      }

    } finally {
      qc.close();
      qc.unregister(ctx);
      if(singleton != null) singleton.unregister();

      if(redirect != null) {
        conn.redirect(redirect);
      } else if(forward != null) {
        conn.forward(forward);
      } else {
        qc.checkStop();
      }
    }

    // write cached result
    if(out instanceof ArrayOutput) {
      final ArrayOutput ao = (ArrayOutput) out;
      final int size = (int) ao.size();
      if(size > 0) conn.res.getOutputStream().write(ao.buffer(), 0, size);
    }
    return response;
  }

  /**
   * Builds a response element and creates the serialization parameters.
   * @param response response element
   * @return serialization parameters
   * @throws QueryException query exception (including unexpected ones)
   */
  private SerializerOptions build(final ANode response) throws QueryException {
    // don't allow attributes
    final BasicNodeIter atts = response.attributes();
    final ANode attr = atts.next();
    if(attr != null) throw func.error(UNEXP_NODE_X, attr);

    // parse response and serialization parameters
    SerializerOptions sp = func.output;
    String cType = null;
    for(final ANode n : response.children()) {
      // process http:response element
      if(HTTP_RESPONSE.eq(n)) {
        // check status and reason
        byte[] sta = null, msg = null;
        for(final ANode a : n.attributes()) {
          final QNm qnm = a.qname();
          if(qnm.eq(Q_STATUS)) sta = a.string();
          else if(qnm.eq(Q_REASON) || qnm.eq(Q_MESSAGE)) msg = a.string();
          else throw func.error(UNEXP_NODE_X, a);
        }
        if(sta != null) {
          status = toInt(sta);
          message = msg != null ? string(msg) : null;
        }

        for(final ANode c : n.children()) {
          // process http:header elements
          if(HTTP_HEADER.eq(c)) {
            final byte[] nam = c.attribute(Q_NAME);
            final byte[] val = c.attribute(Q_VALUE);
            if(nam != null && val != null) {
              final String key = string(nam), value = string(val);
              if(key.equalsIgnoreCase(HttpText.CONTENT_TYPE)) {
                cType = value;
              } else {
                conn.res.setHeader(key, key.equalsIgnoreCase(HttpText.LOCATION) ?
                  conn.resolve(value) : value);
              }
            }
          } else {
            throw func.error(UNEXP_NODE_X, c);
          }
        }
      } else if(OUTPUT_SERIAL.eq(n)) {
        // parse output:serialization-parameters
        sp = FuncOptions.serializer(n, func.output, func.function.info);
      } else {
        throw func.error(UNEXP_NODE_X, n);
      }
    }
    // set content type and serialize data
    if(cType != null) sp.set(SerializerOptions.MEDIA_TYPE, cType);
    return sp;
  }
}

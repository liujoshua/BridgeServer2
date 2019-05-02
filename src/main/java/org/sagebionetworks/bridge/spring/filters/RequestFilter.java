package org.sagebionetworks.bridge.spring.filters;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import com.google.common.base.Joiner;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;

@Component
public class RequestFilter implements Filter {
    
    private static class RequestIdWrapper extends HttpServletRequestWrapper {
        private static final Joiner JOINER = Joiner.on("-");
        private final String requestId;
        RequestIdWrapper(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }
        @Override
        public String getHeader(String name) {
            if (BridgeConstants.X_REQUEST_ID_HEADER.equalsIgnoreCase(name)) {
                return requestId;
            }
            return super.getHeader(name);
        }
        @Override
        public Enumeration<String> getHeaderNames() {
            Vector<String> vector = new Vector<>();
            Enumeration<String> headerNames = super.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                vector.add( headerNameCapitalized(headerNames.nextElement()) );
            }
            if (!vector.contains(BridgeConstants.X_REQUEST_ID_HEADER)) {
                vector.add(BridgeConstants.X_REQUEST_ID_HEADER);    
            }
            return vector.elements();
        }
        // Play leaves these headers capitalized while Spring lowercases them. Our implementation 
        // is not always case insensitive, so we need this behavior to stay the same.
        private String headerNameCapitalized(String headerName) {
            String[] elements = headerName.split("-");
            String[] capitalized = new String[elements.length];
            for (int i=0; i < elements.length; i++) {
                String el = elements[i];
                capitalized[i] = el.substring(0, 1).toUpperCase() + 
                        el.substring(1, el.length()).toLowerCase();
            }
            return JOINER.join(capitalized);
        }
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Set request ID in a request-scoped context object. This object will be replaced 
        // with further user-specific security information, if the controller method that 
        // was intercepted retrieves the user's session (this code is consolidated in the 
        // BaseController). For unauthenticated/public requests, we do *not* want a 
        // Bridge-Session header changing the security context of the call.
        
        HttpServletRequest request = (HttpServletRequest)req;
        String requestId = request.getHeader(BridgeConstants.X_REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = generateRequestId();
        }
        RequestContext.Builder builder = new RequestContext.Builder().withRequestId(requestId);
        setRequestContext(builder.build());

        req = new RequestIdWrapper((HttpServletRequest)req, requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            // Clear request context when finished.
            setRequestContext(null);
        }
    }
    
    // Isolated for testing
    protected String generateRequestId() {
        return BridgeUtils.generateGuid();
    }
    
    // Isolated for testing
    protected void setRequestContext(RequestContext context) {
        BridgeUtils.setRequestContext(context);
    }
}

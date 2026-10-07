package com.example.repro;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Reproducer for an Open Liberty regression (26.0.0.5+).
 *
 * This filter is mapped to /j_security_check (the container-handled FORM-login action URL).
 * It calls filterChain.doFilter() FIRST, which is when the container actually performs the
 * FORM-login authentication, and only AFTER that returns does it call
 * request.getUserPrincipal() to find out who just logged in.
 *
 * This is a long-standing, widely used idiom in Servlet containers (Tomcat, WildFly, GlassFish,
 * WebSphere traditional, and Open Liberty itself up to 26.0.0.4) for post-login bookkeeping
 * (audit logging, custom session tagging, etc.) without needing a custom LoginModule/JASPIC SAM.
 *
 * On Open Liberty 26.0.0.5+, request.getUserPrincipal() called at this exact point returns
 * null, even though authentication just succeeded (every subsequent request on the same
 * session correctly reports the authenticated principal). This filter records what it saw into
 * the session attribute "postLoginPrincipalName", which welcome.jsp then displays, making the
 * bug immediately visible: expected the username, but you will see "NULL (BUG REPRODUCED)".
 */
public class PostLoginCaptureFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;

        // This triggers the container's FORM-login authentication for j_security_check.
        chain.doFilter(request, response);

        // Immediately after authentication completes: this is the call that is expected to
        // return the just-authenticated Principal, but returns null on 26.0.0.5+.
        Principal principal = httpReq.getUserPrincipal();

        HttpSession session = httpReq.getSession(false);
        if (session != null) {
            String captured = (principal == null) ? "NULL (BUG REPRODUCED)" : principal.getName();
            session.setAttribute("postLoginPrincipalName", captured);
            session.setAttribute("postLoginCapturedAtMillis", System.currentTimeMillis());
        }
    }

    @Override
    public void destroy() {
    }
}

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<html>
<head><title>Welcome</title></head>
<body>
<h1>Welcome, <%= request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "(no principal on THIS request?!)" %></h1>

<p><b>Principal captured by PostLoginCaptureFilter immediately after login (right after
filterChain.doFilter() returned from /j_security_check):</b></p>
<pre style="font-size: 1.3em; padding: 10px; border: 2px solid black;">
<%= session.getAttribute("postLoginPrincipalName") %>
</pre>

<p>Expected: the logged-in username (e.g. <code>reprouser</code>).<br/>
Actual on Open Liberty 26.0.0.5+: <code>NULL (BUG REPRODUCED)</code>, even though this very page
load above correctly shows <code>request.getUserPrincipal()</code> as non-null, proving the user
really is authenticated - only the call made immediately after the login filter's
<code>chain.doFilter()</code> returns is affected.</p>

<p>This exact pattern is used by real applications to audit/record login attempts right after
FORM-login success; when it silently fails, the application cannot tell a successful login from
a failed one.</p>
</body>
</html>

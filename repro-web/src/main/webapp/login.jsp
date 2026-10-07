<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<html>
<head><title>Login</title></head>
<body>
<h1>Login</h1>
<form method="POST" action="j_security_check">
    Username: <input type="text" name="j_username"/><br/>
    Password: <input type="password" name="j_password"/><br/>
    <input type="submit" value="Login"/>
</form>
<p>Use the test user configured in server.xml's basicRegistry, e.g. reprouser / reprouser</p>
</body>
</html>

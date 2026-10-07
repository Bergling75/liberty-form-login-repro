# liberty-form-login-repro

Minimal reproducer for an Open Liberty regression. Confirmed:
- **Works correctly on 26.0.0.4** (verified directly with this reproducer).
- **Broken on 26.0.0.5+** (verified directly with this reproducer; also originally observed in a
  real application upgrading from 26.0.0.4 to 26.0.0.5).

> `request.getUserPrincipal()`, called immediately after `filterChain.doFilter()` returns inside a
> `Filter` mapped to `/j_security_check`, returns `null` - even though the FORM-login
> authentication that `doFilter()` just triggered succeeded. Every subsequent request on the same
> session correctly reports the authenticated principal; only this one call, made at this one
> point in request processing, is affected.

This is a well-known idiom for FORM-login post-processing (audit logging of login attempts,
custom session tagging, redirect customization, etc.) used across many Java EE/Jakarta EE
containers (Tomcat, WildFly, GlassFish, WebSphere traditional, and Open Liberty itself through
26.0.0.3/26.0.0.4). When it silently breaks, an application can no longer tell a successful login
from a failed one.

## What's in this project

- `repro-web` - a trivial WAR:
  - `WEB-INF/web.xml` - FORM login config (`/j_security_check`), a security constraint protecting
    `welcome.jsp`, and a filter mapping for `PostLoginCaptureFilter` on `/j_security_check`.
  - `PostLoginCaptureFilter.java` - the filter under test. Calls `chain.doFilter()` (which is when
    the container authenticates the FORM login), then immediately calls
    `request.getUserPrincipal()` and stores what it saw (`"NULL (BUG REPRODUCED)"` or the
    username) into the session as `postLoginPrincipalName`.
  - `login.jsp` / `loginError.jsp` - plain FORM login pages.
  - `welcome.jsp` - the protected page. Displays the *current* request's principal (always correct,
    proving the user really is logged in) side-by-side with what `PostLoginCaptureFilter` captured
    right after login (shows the bug when it reproduces).
- `repro-ear` - wraps `repro-web` into an EAR, matching how the real-world application that
  uncovered this bug is deployed.
- `server-config/server.xml` - a minimal Liberty server config using a `basicRegistry` (no
  LDAP/DB dependency) with one test user, `reprouser` / `reprouser`.

## Build

```
mvn clean package
```

This produces `repro-ear/target/repro-ear.ear`.

## Deploy & run

1. Create/point a Liberty server at `server-config/server.xml` (or merge its contents into your
   own `server.xml`). Note it uses ports 9081/9444 to avoid clashing with a default server.
2. Copy `repro-ear/target/repro-ear.ear` into that server's `apps/` directory (referenced by
   `server.xml` as `repro-ear.ear`).
3. Start the server, then open `http://localhost:9081/repro/` in a browser.
4. Click through to the protected page; log in as `reprouser` / `reprouser`.
5. On the resulting `welcome.jsp` page, compare:
   - The page's own `request.getUserPrincipal()` (always correct - proves you're logged in).
   - The boxed value captured by `PostLoginCaptureFilter` immediately after login.

## Confirmed reproduction

- Verified by hand against a local Open Liberty **26.0.0.3** install: logging in as
  `reprouser`/`reprouser` and viewing `welcome.jsp` correctly shows `reprouser` as the value
  captured by `PostLoginCaptureFilter` - no bug.
- Verified against a local Open Liberty **26.0.0.5** install by driving the FORM-login flow
  programmatically (`GET /repro/welcome.jsp` → redirected to login → `POST /repro/j_security_check`
  with `reprouser`/`reprouser`). Result on `welcome.jsp`:

```
Welcome, reprouser                                   <- this request's getUserPrincipal() is correct
Principal captured by PostLoginCaptureFilter: NULL (BUG REPRODUCED)
```

confirming the bug reproduces with just this minimal EAR/WAR and a `basicRegistry`, no LDAP/DB/EJB
involved, and that the regression window is between 26.0.0.3 and 26.0.0.5.

## Expected vs actual

| Liberty version | Value captured by `PostLoginCaptureFilter` |
|---|---|
| 26.0.0.4 (confirmed), other containers | `reprouser` |
| 26.0.0.5+ (confirmed) | `NULL (BUG REPRODUCED)` |

## Why this matters

Applications that rely on this idiom to record a login attempt (e.g., writing an audit-trail
record and tagging the session with its ID) will silently fail to do so. If anything downstream
then enforces "every authenticated session must have a recorded login attempt" (a reasonable
session-integrity/audit check), legitimate users get logged out again on their very next request,
even though they just logged in successfully. This was the real-world symptom that led to this
reproducer: users could log in, but clicking any link immediately redirected them back to the
login page.

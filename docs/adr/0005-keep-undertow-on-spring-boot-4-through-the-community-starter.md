---
status: accepted
---

# Keep Undertow on Spring Boot 4 through the community starter

Spring Boot 4 removed `spring-boot-starter-undertow`, and WebFlux defaults to Netty. We keep Undertow by using the community `io.undertow:undertow-spring-boot-starter` (repo `undertow-io/spring-boot-undertow`, Undertow EE 2.0.2, Servlet 6.1, built against Boot 4.1.1) and excluding `spring-boot-starter-reactor-netty` from `spring-boot-starter-webflux`.

We chose this over Netty (the default), Tomcat and Jetty because the app already runs on Undertow and we want to stay on it.

## Consequences

- The starter's only version is `1.0.0.Alpha1-SNAPSHOT`, which is not on Maven Central. It is built from source and installed into the local `~/.m2`, so a fresh clone or CI cannot resolve it until it is installed there.
- When a release is published, set `undertow-starter.version` in `backend/pom.xml` to it, adding its repository if it is not on Central, and remove the TODO comment above the dependency.
- Read the starter's `MIGRATION.md` and `CHANGELOG.md` when bumping: `server.undertow.*` properties are deprecated in favour of `undertow.server.*` and will be removed. We set none today.
- The snapshot's reactive web server factory does not register `WebSocketDeploymentInfo` on its servlet deployment (only the servlet factory does), so `/api/ws` fails with `ServletContext attribute 'jakarta.websocket.server.ServerContainer' not found`. Our local build carries a patch to `UndertowReactiveWebServerFactory` that adds that attribute when `io.undertow.websockets.jsr.WebSocketDeploymentInfo` is on the classpath. A published release must include the same fix; check the WebSocket upgrade (`101 Switching Protocols` on `/api/ws`) before dropping the local build.

# Securing Spring Integration  with the Spring Authorization Server

Hi, Spring fans! In this installment, we look at how to secure headless, back-office processes with JWT sourced from a Spring Authorization Server instance.


It's easy enough to figure out how to secure HTTP endpoints. We've all done that. And you can find more on [that in the code presented here](https://github.com/coffee-software-show/authorization-server-in-boot-31) or by watching this fabulous livestream I did  with [Spring Security legend with Steve Riesenberg](https://www.youtube.com/watch?v=7zm3mxaAFWk) _waay_ back in the halcyon days of May, 2023. 

But what about a backoffice process? Something without a browser like a cronjob, a batch `Job`, a Spring Integration messaging flow, etc.? This will be the focus of today's blog. There are a  few pieces we need to understand:

* the Spring Authorization Server, and OAuth 
* obtaining an OAuth token for a headless client 
* validating that token in a headless process 


## Introducing the Spring Authorization Server 

That livestream with Steve is really persuasive. In it, Steve introduces the full sweep of common use cases supported by the various Spring Security OAuth modules: 

* OAuth clients: an application may prompt a user to redirect to an authorization server and then, once redirected back, allow the user to continue their session with an authenticated `Principal` in the context. This is useful for the frontend applications that people interact with directly.
* OAuth resource servers: an API - such as a HTTP/REST application - may be configured to reject requests without a token in tow
* OAuth authorization server: this is the crazy part, and the _raison d'etre_ for the livestream: there's new autoconfiguration and starters in Spring Boot 3.1 to standup a brand new, fully configured, Spring Authorization Server instance with nary a few properties. 

There used to be a project called Spring Security OAuth. It was a standalone project, different from today's OAuth support in the Spring Security project. It got long in the tooth so we slowly started rebuilding it and then folding the new support into Spring Security itself. First the OIDC/client support, then the Resource Server support. And that's where we thought we'd stop: we didn't plan on rebuilding the old Spring Security OAuth authorization server functionality, figuring there were countless other modules people could use or pay for: Okta, Auth0, Active Directory, Keycloak, etc. But you, dear community, told us differently. We listened and, _voilà_, the Spring Authorization Server was born. 

And now that it's supported out of the box with Spring Boot, it's never been easier to standup a full-featured OAuth IDP (identity provider)!

Go to [the Spring Initializr](https://start.spring.io) and choose `OAuth2 Authorization Server`. (and why not add `GraalVM`, for good measure?). Open up the `application.properties` file and add the following configuration: 

```properties
spring.security.oauth2.authorizationserver.client.client-1.registration.client-id=cseai
spring.security.oauth2.authorizationserver.client.client-1.registration.client-secret={bcrypt}$2a$10$Sf/jdMbtr6hByUOByaYYZuA/beZVsz0v7Zz22YhRPjmH8f20AcWaa
spring.security.oauth2.authorizationserver.client.client-1.registration.client-authentication-methods=client_secret_basic
spring.security.oauth2.authorizationserver.client.client-1.registration.authorization-grant-types=client_credentials
spring.security.oauth2.authorizationserver.client.client-1.registration.scopes=user.read,user.write,openid
```

It may seem like a lot, but all this is doing is telling the Authorization Server that there is a client, called `cseai`, that has a BCrypt hashed password, `cseai`. (And yes, I know it's a terrible password! It was a demo! Don't `@` me!), and that will have whatever (arbitrary) scopes (rights) we give it: here, we've given it `user.read`, `user.writer`, and `openid`. You can add other arbitrary ones. The authentication method is `client_secret_basic`, and - importantly - the authorization grant  type is `client_credentials`. 


The password is hashed using the BCrypt algorithm. You can easily generate new BCrypt-ed passwords using the [`spring encodepassword` CLI command](https://docs.spring.io/spring-boot/docs/current/reference/html/cli.html). If I wanted to encode the password `p@ssw0rd`, I would say `spring encodepassword p@ssw0rd`. This yeilds `{bcrypt}$2a$10$hhSzg.TO9a3cbEVFf.53S.GmxqyoymtYB3zd2PoC87es9ffPp/lfu`. 


### On OAuth Clients 

OAuth clients typically connect using the three-legged OAuth authentication flow: the client (a user in a browser, for example) hits a secured endpoint, is redirected to an OAuth IDP (like Spring Authorization Server), logs in (I'm sure you've used the `Sign in with Google` button on various websites), and then is redirected back to the secured endpoint, but this time with a token in tow, allowing the client access to the secured endpoint. To enable this sort of login, you might specify that the `authorization-grant-types` is `authorization_code`  and `refresh_token`. In this blog, however, we want to demonstrate how to secure a headless (meaning it has no HTTP endpoints or any externally visible surface area) backoffice process (like a Spring Integration messaging flow, or a Spring Batch job, or a Spring Cloud `Task` of some sort) by registering it as a client in the Spring Authorization Server, but a client with no user context.

Clients, in OAuth, represent either autonomous processes or human beings (users) with a well-known set of privileges associated with it. Imagine you're using the Twitter/X API or the Facebook/Meta APIs. Suppose you want your code to be able to look at ananlyse any particular users' posts to their favorite respective social media application in the browser, but in order to do that they'll need to be redirected to X or Meta, authenticate there, then redirect back to your application in the browser. In this case, the _client_ is the browser application that you've built. Tokens obtained on behalf of these users for that client contain information enough to identify the user: this request is from Stéphane, that request is from Madhura, etc. 

In a backoffice process, however, there's not necessarily any user context. The job isn't running on behalf of anyone. We just want to know that requests sent into the backoffice process are from well-known, and trusted, clients. 

The first kind of client can take for granted the presence of a browser, and the second can not. The Spring Authorization Server can support both of them.


## Spring Integration 

You know, I love Spring Integration. Spring Integration makes it trivial for me to connect disparate systems and services. The framework was born of the patterns described in the seminal tome [_Enterprise Integration Patterns_](https://www.enterpriseintegrationpatterns.com/). The book describes four strategies for integrating systems: 

* RPC - remote procedure calls. A producer and a consumer are coupled to each other and to the payloads they can produce and accept. If a service is down, then the request will fail, as opposed to be delayed.
* 


<!-- 

* introduce a minimal Spring Authorization Server
* introduce docker compose
* introduce the producer 
* introduce the consumer 




-->
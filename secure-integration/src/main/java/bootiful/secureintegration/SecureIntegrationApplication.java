package bootiful.secureintegration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.util.Assert;


@SpringBootApplication
public class SecureIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureIntegrationApplication.class, args);
    }

}

@Configuration
class IntegrationConfiguration {

    private final String authorizationHeaderName = "Authorization";

    private final String destinationName = "requests";

    @Bean
    Exchange exchange() {
        return ExchangeBuilder.directExchange(this.destinationName).build();
    }

    @Bean
    Binding binding() {
        return BindingBuilder.bind(this.queue()).to(this.exchange()).with(this.destinationName).noargs();
    }

    @Bean
    Queue queue() {
        return QueueBuilder.durable(this.destinationName).build();
    }


    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.authorizationserver.issuer}") String issuerUri) {
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }

    @Bean
    IntegrationFlow inboundFlow(MessageChannel inbound, ConnectionFactory connectionFactory) throws Exception {
        var amqpInboundAdapter = Amqp.inboundAdapter(connectionFactory, this.destinationName);
        return IntegrationFlow
                .from(amqpInboundAdapter)
                .channel(inbound)
                .get();

    }

    @Bean
    IntegrationFlow loggingInboundFlow(MessageChannel inbound) {
        return IntegrationFlow
                .from(inbound)
                .handle((GenericHandler<String>) (payload, headers) -> {
                    System.out.println("got a new file whose contents are %s".formatted(payload));
                    headers.forEach((key, value) -> System.out.println(key + '=' + value));
                    return null;
                })
                .get();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }

    @Bean
    JwtAuthenticationProvider authenticationProvider(JwtDecoder jwtDecoder) {
        return new JwtAuthenticationProvider(jwtDecoder);
    }

    @Bean
    DirectChannelSpec inbound(JwtAuthenticationProvider jwtAuthenticationProvider) {
        return MessageChannels
                .direct()
                .interceptor(
                        new JwtAuthenticationInterceptor(this.authorizationHeaderName, jwtAuthenticationProvider),
                        new SecurityContextChannelInterceptor(this.authorizationHeaderName),
                        new AuthorizationChannelInterceptor(AuthenticatedAuthorizationManager.authenticated())
                );
    }

    static class JwtAuthenticationInterceptor implements ChannelInterceptor {

        private final String authorizationHeaderName;
        private final JwtAuthenticationProvider jwtAuthenticationProvider;

        JwtAuthenticationInterceptor(String authorizationHeaderName, JwtAuthenticationProvider jwtAuthenticationProvider) {
            this.authorizationHeaderName = authorizationHeaderName;
            this.jwtAuthenticationProvider = jwtAuthenticationProvider;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {

            var jwt = (String) message.getHeaders().get(this.authorizationHeaderName);
            Assert.hasText(jwt, "the JWT text is empty and should not be");

            var authentication = this.jwtAuthenticationProvider.authenticate(new BearerTokenAuthenticationToken(jwt));
            if (authentication != null && authentication.isAuthenticated()) {
                var user = new User(authentication.getName()); //todo look this up in SQL DB for business logic purposes
                var upt = UsernamePasswordAuthenticationToken.authenticated(user, null, AuthorityUtils.NO_AUTHORITIES);

                return MessageBuilder
                        .fromMessage(message)
                        .setHeader(this.authorizationHeaderName, upt)
                        .build();
            }
            return MessageBuilder.fromMessage(message).setHeader(this.authorizationHeaderName, null).build();
        }
    }


    record User(String username) {
    }

}


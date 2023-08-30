package bootiful.secureintegrationclient;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class SecureIntegrationClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureIntegrationClientApplication.class, args);
    }

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
    Client client(MessageChannel outbound, RestTemplate restTemplate,
                  @Value("${bootiful.client-id:cseai}") String clientId,
                  @Value("${bootiful.client-secret:cseai}") String clientSecret) {
        return new Client(outbound, restTemplate, clientId, clientSecret);
    }

    @Bean
    ApplicationRunner runner(Client client) {
        return args -> client.send("hello world @ " + System.currentTimeMillis());
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    DirectChannelSpec outbound() {
        return MessageChannels.direct();
    }

    @Bean
    IntegrationFlow outboundFlow(AmqpTemplate amqpTemplate) {

        var amqpOutboundAdapter = Amqp
                .outboundAdapter(amqpTemplate)
                .routingKey(this.destinationName);

        return IntegrationFlow
                .from(this.outbound())
                .handle(amqpOutboundAdapter)
                .get();
    }


}

class Client {

    private final MessageChannel channel;

    private final RestTemplate restTemplate;

    private final String clientId, clientSecret;

    Client(MessageChannel channel, RestTemplate restTemplate, String clientId, String clientSecret) {
        this.channel = channel;
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    private String getJwtToken() {
        var headers = new HttpHeaders();
        headers.setBasicAuth(this.clientId, this.clientSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        var body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "user.read");

        var entity = new HttpEntity<>(body, headers);
        var url = "http://localhost:8080/oauth2/token";
        var response = this.restTemplate.postForEntity(url, entity, JsonNode.class);
        Assert.state(response.getStatusCode().is2xxSuccessful(), "the response needs to be 200x");
        return response.getBody().get("access_token").asText();
    }

    public void send(String txt) {
        var jwtToken = getJwtToken();
        System.out.println("got the jwt token: " + jwtToken);
        var message = MessageBuilder
                .withPayload(txt)
                .setHeader("Authorization", jwtToken)
                .build();
        this.channel.send(message);
    }
}
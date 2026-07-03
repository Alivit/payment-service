package com.minispring.paymentservice.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BearerTokenInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(channel.newCall(methodDescriptor, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {

                try {
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(
                                    "order-service-internal")
                            .principal("order-service")
                            .build();

                    OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);

                    if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                        String token = authorizedClient.getAccessToken().getTokenValue();
                        headers.put(AUTHORIZATION_KEY, "Bearer " + token);
                    } else {
                        log.warn("Failed to obtain Service Account token for gRPC call");
                    }
                } catch (Exception e) {
                    log.error("Error fetching OAuth2 token for gRPC communication", e);
                }

                super.start(responseListener, headers);
            }
        };
    }
}

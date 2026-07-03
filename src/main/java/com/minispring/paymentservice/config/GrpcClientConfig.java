package com.minispring.paymentservice.config;

import com.minispring.grpc.order.OrderGrpcServiceGrpc;
import com.minispring.paymentservice.client.BearerTokenInterceptor;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public OrderGrpcServiceGrpc.OrderGrpcServiceBlockingStub orderGrpcStub(
            GrpcChannelFactory channelFactory, BearerTokenInterceptor bearerTokenInterceptor) {
        Channel channel = channelFactory.createChannel("order-service-grpc");
        Channel interceptedChannel = ClientInterceptors.intercept(channel, bearerTokenInterceptor);
        return OrderGrpcServiceGrpc.newBlockingStub(interceptedChannel);
    }
}

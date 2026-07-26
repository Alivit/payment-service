package com.minispring.paymentservice.mapper;

import com.minispring.grpc.order.OrderPriceDto;
import com.minispring.paymentservice.dto.response.OrderView;
import com.minispring.paymentservice.dto.response.PaymentView;
import com.minispring.paymentservice.messaging.event.PaymentCreateEvent;
import com.minispring.paymentservice.model.Payment;
import org.mapstruct.*;

@Mapper(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true))
public interface PaymentMapper {

    @Mapping(source = "paymentAmount", target = "amount")
    @Mapping(source = "paymentStatus", target = "status")
    PaymentView toView(Payment payment);

    @Mapping(source = "id", target = "paymentId")
    @Mapping(source = "paymentStatus", target = "status")
    PaymentCreateEvent toEvent(Payment payment);

    @Mapping(target = "orderId", expression = "java(java.util.UUID.fromString(dto.getOrderId()))")
    @Mapping(target = "userId", expression = "java(java.util.UUID.fromString(dto.getUserId()))")
    @Mapping(target = "totalPrice", expression = "java(new java.math.BigDecimal(dto.getTotalPrice()))")
    OrderView toOrderPriceDetails(OrderPriceDto dto);

    @Mapping(target = "paymentAmount", source = "totalPrice")
    @Mapping(target = "paymentStatus", constant = "PENDING")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Payment toNewPayment(OrderView orderDetails);
}

package com.minispring.paymentservice.mapper;

import com.minispring.paymentservice.dto.PaymentCreateDto;
import com.minispring.paymentservice.dto.PaymentResponseDto;
import com.minispring.paymentservice.model.Payment;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        builder = @Builder(disableBuilder = true))
public interface PaymentMapper {

    @Mapping(source = "amount", target = "paymentAmount")
    Payment paymentCreateDtoToPayment(PaymentCreateDto dto);

    @Mapping(source = "paymentAmount", target = "amount")
    @Mapping(source = "paymentStatus", target = "status")
    PaymentResponseDto paymentToPaymentResponseDto(Payment payment);
}

package com.example.paymentservice.mapper;

import com.example.paymentservice.dto.PaymentCreateDto;
import com.example.paymentservice.dto.PaymentResponseDto;
import com.example.paymentservice.model.Payment;
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

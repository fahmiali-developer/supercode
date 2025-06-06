package com.supercode.service;

import com.supercode.dto.PaymentDTO;
import com.supercode.dto.PaymentSourceDTO;
import com.supercode.dto.TransactionDTO;
import com.supercode.entity.HeaderPayment;
import com.supercode.repository.*;
import com.supercode.request.GeneralRequest;
import com.supercode.entity.PaymentMethod;
import com.supercode.response.BaseResponse;
import com.supercode.util.MessageConstant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class TransactionService {


    @Inject
    PaymentMethodRepository paymentMethodRepository;

    @Inject
    HeaderPaymentRepository headerPaymentRepository;

    @Inject
    PosRepository posRepository;

    @Inject
    DetailPaymentAggregatorRepository detailPaymentAggregatorRepository;

    @Inject
    LogReconRepository logReconRepository;

    @Inject
    BankMutationRepository bankMutationRepository;

    public Response getAllPaymentTransaction() {
        BaseResponse baseResponse;
        try {
            // Ambil semua metode pembayaran yang statusnya aktif dari database
            List<PaymentMethod> paymentMethods = paymentMethodRepository.listAll();

            // Mapping kategori pembayaran berdasarkan payment_type dari database
            Map<String, List<PaymentSourceDTO>> groupedPayments = new LinkedHashMap<>();

            for (PaymentMethod pm : paymentMethods) {
                String category = pm.getPaymentType(); // Gunakan payment_type sebagai kategori
                groupedPayments.putIfAbsent(category, new ArrayList<>());
                groupedPayments.get(category).add(new PaymentSourceDTO(pm.getPaymentMethod(), pm.getPmId()));
            }

            // Konversi ke List<PaymentDTO>
            List<PaymentDTO> listPayment = groupedPayments.entrySet().stream()
                    .map(entry -> new PaymentDTO(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // Format response
            Map<String, Object> payload = new HashMap<>();
            payload.put("listPayment", listPayment);

            baseResponse = new BaseResponse(200, "Successfully retrieved payment methods");
            baseResponse.payload = payload;
            return Response.status(baseResponse.result).entity(baseResponse).build();

        } catch (Exception e) {
            e.printStackTrace();
            baseResponse = new BaseResponse(500, "Failed to retrieve payment methods");
            return Response.status(baseResponse.result).entity(baseResponse).build();
        }
    }

    public Response getBranchTransaction(GeneralRequest request) {
        BaseResponse baseResponse;
        boolean statusReconAll=MessageConstant.TRUE_VALUE;
        List<TransactionDTO.TransactionList> transactionLists = new ArrayList<>();
        try {
            TransactionDTO transactionDTO = new TransactionDTO();
            transactionDTO.setTransactionDate(request.getTransDate());
            transactionDTO.setBranchId(request.getBranchId());
            List<HeaderPayment> headerPayments = headerPaymentRepository.getByTransDateAndBranchId(request.getTransDate(), request.getBranchId());
            int submitStatus = logReconRepository.getSubmitStatus(request);
            for(HeaderPayment headerPayment : headerPayments){
                boolean statusRecon=MessageConstant.TRUE_VALUE;
                TransactionDTO.TransactionList transactionList = new TransactionDTO.TransactionList();
                String transactionSource = paymentMethodRepository.getPaymentMethodByPmId(headerPayment.getPmId());
                BigDecimal amount;
                if(transactionSource.equalsIgnoreCase(MessageConstant.POS)){
                    if(!headerPayment.getStatusRekonPosVsEcom().equalsIgnoreCase("true")){
                        statusReconAll=MessageConstant.FALSE_VALUE;
                        statusRecon = MessageConstant.FALSE_VALUE;
                    }
                    amount = posRepository.getAmountByParentId(headerPayment.getParentId());
                }else if(transactionSource.equalsIgnoreCase("BCA") || transactionSource.equalsIgnoreCase("Bank Mandiri")){
                    if(!headerPayment.getStatusRekonEcomVsBank().equalsIgnoreCase("true")){
                        statusReconAll=MessageConstant.FALSE_VALUE;
                        statusRecon = MessageConstant.FALSE_VALUE;
                    }
                    amount = bankMutationRepository.getAmountByParentId(headerPayment.getParentId());
                }
                else{
                    amount = detailPaymentAggregatorRepository.getAmountByParentId(headerPayment.getParentId());
                    if(!headerPayment.getStatusRekonEcomVsPos().equals("1")){
                        statusReconAll=MessageConstant.FALSE_VALUE;
                        statusRecon = MessageConstant.FALSE_VALUE;
                    }
                }
                transactionList.setPaymentId(headerPayment.getPmId());
                transactionList.setTransactionSource(transactionSource);
                transactionList.setAmount(amount);
                transactionList.setCreatedAt(headerPayment.getCreatedAt());
                transactionList.setStatusRecon(statusRecon);
                transactionList.setFileName(headerPayment.getFileName());
                transactionLists.add(transactionList);
            }
            transactionDTO.setSubmitStatus(submitStatus);
            transactionDTO.setStatusRecon(statusReconAll);
            transactionDTO.setTransactionList(transactionLists);

            baseResponse = new BaseResponse(200, "Successfully retrieved payment transactions");
            baseResponse.payload = transactionDTO;
            return Response.status(baseResponse.result).entity(baseResponse).build();
        }catch (Exception e){
            e.printStackTrace();
            baseResponse = new BaseResponse(500, "Failed to retrieve payment transactions");
            return Response.status(baseResponse.result).entity(baseResponse).build();
        }
    }

    public Response getBranchDashboarTransaction(GeneralRequest request) {
        BaseResponse baseResponse;
        BigDecimal allGrossAmount = BigDecimal.ZERO;
        BigDecimal allNettAmount = BigDecimal.ZERO;
        List<TransactionDTO.TransactionList> transactionLists = new ArrayList<>();
        try {
            // get pm by method digital
            List<String> pmIds = paymentMethodRepository.getPaymentMethodByGroup(MessageConstant.DIGITAL);
            TransactionDTO transactionDTO = new TransactionDTO();
            transactionDTO.setTransactionDate(request.getTransDate());
            transactionDTO.setBranchId(request.getBranchId());
            for(String pmId : pmIds){
                TransactionDTO.TransactionList transactionList = new TransactionDTO.TransactionList();
                String transactionSource = paymentMethodRepository.getPaymentMethodByPmId(pmId);
                transactionList.setPaymentId(pmId);
                transactionList.setTransactionSource(transactionSource);
                BigDecimal amount = detailPaymentAggregatorRepository.getAmountByParentIdByRequest(request, pmId, "gross_amount");
                BigDecimal amountNet = detailPaymentAggregatorRepository.getAmountByParentIdByRequest(request, pmId, "net_amount");

                allNettAmount = allNettAmount.add(amountNet);
                allGrossAmount = allGrossAmount.add(amount);
                transactionList.setAmount(amount);
                transactionLists.add(transactionList);
            }
            transactionDTO.setTransactionList(transactionLists);
            transactionDTO.setAllNet(allNettAmount);
            transactionDTO.setAllGross(allGrossAmount);
            baseResponse = new BaseResponse(200, "Successfully retrieved payment transactions");
            baseResponse.payload = transactionDTO;
            return Response.status(baseResponse.result).entity(baseResponse).build();
        }catch (Exception e){
            e.printStackTrace();
            baseResponse = new BaseResponse(500, "Failed to retrieve payment transactions");
            return Response.status(baseResponse.result).entity(baseResponse).build();
        }
    }
}

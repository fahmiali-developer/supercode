package com.supercode.dto;

import java.math.BigDecimal;
import java.util.List;

public class TransactionDTO {
    private String transactionDate;
    private String branchId;
    private boolean statusRecon;
    private List<TransactionList> transactionList;


    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public boolean isStatusRecon() {
        return statusRecon;
    }

    public void setStatusRecon(boolean statusRecon) {
        this.statusRecon = statusRecon;
    }

    public List<TransactionList> getTransactionList() {
        return transactionList;
    }

    public void setTransactionList(List<TransactionList> transactionList) {
        this.transactionList = transactionList;
    }

    public static class TransactionList{
        private String  transactionSource;
        private BigDecimal amount;
        boolean statusRecon;

        public String getTransactionSource() {
            return transactionSource;
        }

        public void setTransactionSource(String transactionSource) {
            this.transactionSource = transactionSource;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public boolean isStatusRecon() {
            return statusRecon;
        }

        public void setStatusRecon(boolean statusRecon) {
            this.statusRecon = statusRecon;
        }
    }
}

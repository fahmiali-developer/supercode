package com.supercode.entity;

import com.supercode.util.MessageConstant;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Entity
@Table(name = "bank_mutation")
public class BankMutation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_mutation_id", length = 50, nullable = false)
    private Long bankMutationId;

    @Column(name = "bank", length = 100, nullable = false)
    private String bank;

    @Column(name = "trans_date", nullable = false)
    private Date transDate;

    @Column(name = "notes", length = 150)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "debit_credit", nullable = false)
    private DebitCredit debitCredit;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_by", length = 50, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private String createdAt;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "changed_by", length = 50)
    private String changedBy;

    @Column(name = "changed_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date changedAt;

    @Column(name = "changed_on")
    @Temporal(TemporalType.DATE)
    private Date changedOn;

    @Column(name = "account_no", length = 100)
    private String accountNo;

    @Column(name = "parent_id", length = 100)
    private String parentId;

    // Getters and Setters
    // Constructor (Default & Parameterized)
    // toString(), equals(), and hashCode() methods


    public Long getBankMutationId() {
        return bankMutationId;
    }

    public void setBankMutationId(Long bankMutationId) {
        this.bankMutationId = bankMutationId;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }

    public Date getTransDate() {
        return transDate;
    }

    public void setTransDate(Date transDate) {
        this.transDate = transDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public DebitCredit getDebitCredit() {
        return debitCredit;
    }

    public void setDebitCredit(DebitCredit debitCredit) {
        this.debitCredit = debitCredit;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Date getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Date changedAt) {
        this.changedAt = changedAt;
    }

    public Date getChangedOn() {
        return changedOn;
    }

    public void setChangedOn(Date changedOn) {
        this.changedOn = changedOn;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }


    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime createdAts = LocalDateTime.now();
        String timeOnly = createdAts.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        createdAt = timeOnly;
        createdOn=LocalDateTime.now();
    }
}


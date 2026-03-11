package com.sc.service;

import com.sc.entity.StudentEntity;
import com.sc.entity.FeesEntity;

public interface EmailService {

    /**
     * Send fee reminder email to student with pending fees
     */
    void sendFeeReminderEmail(StudentEntity student, FeesEntity fees);

    /**
     * Send installment due email
     */
    void sendInstallmentDueEmail(StudentEntity student, FeesEntity fees, Long installmentId);

    /**
     * Send overdue reminder email
     */
    void sendOverdueReminderEmail(StudentEntity student, FeesEntity fees, Long installmentId);

    /**
     * Send payment confirmation email
     */
    void sendPaymentConfirmationEmail(StudentEntity student, FeesEntity fees, Integer amount, String transactionId);
}
package com.sc.service.serviceImpl;

import com.sc.entity.StudentEntity;
import com.sc.entity.FeesEntity;
import com.sc.entity.Installment;
import com.sc.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${school.name:Kunash School}")
    private String schoolName;

    @Value("${app.base.url:http://localhost:8084}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ============= 📧 FEE REMINDER EMAIL =============

    @Override
    public void sendFeeReminderEmail(StudentEntity student, FeesEntity fees) {
        try {
            String to = getStudentEmail(student);
            String subject = "Fee Payment Reminder - " + schoolName;

            String htmlContent = buildFeeReminderHtml(student, fees);

            sendEmail(to, subject, htmlContent);

            logger.info("Fee reminder email sent successfully to: {}", to);

        } catch (Exception e) {
            logger.error("Failed to send fee reminder email: {}", e.getMessage());
        }
    }

    // ============= 📧 INSTALLMENT DUE EMAIL =============

    @Override
    public void sendInstallmentDueEmail(StudentEntity student, FeesEntity fees, Long installmentId) {
        try {
            String to = getStudentEmail(student);
            String subject = "Installment Due Reminder - " + schoolName;

            Installment installment = fees.getInstallmentsList().stream()
                    .filter(i -> installmentId.equals(i.getInstallmentId()))
                    .findFirst()
                    .orElse(null);

            if (installment == null) {
                logger.error("Installment not found with ID: {}", installmentId);
                return;
            }

            String htmlContent = buildInstallmentDueHtml(student, fees, installment);
            sendEmail(to, subject, htmlContent);

            logger.info("Installment due email sent successfully to: {}", to);

        } catch (Exception e) {
            logger.error("Failed to send installment due email: {}", e.getMessage());
        }
    }

    // ============= 📧 OVERDUE REMINDER EMAIL =============

    @Override
    public void sendOverdueReminderEmail(StudentEntity student, FeesEntity fees, Long installmentId) {
        try {
            String to = getStudentEmail(student);
            String subject = "Payment Overdue Reminder - " + schoolName;

            Installment installment = fees.getInstallmentsList().stream()
                    .filter(i -> installmentId.equals(i.getInstallmentId()))
                    .findFirst()
                    .orElse(null);

            if (installment == null) {
                logger.error("Installment not found with ID: {}", installmentId);
                return;
            }

            String htmlContent = buildOverdueReminderHtml(student, fees, installment);
            sendEmail(to, subject, htmlContent);

            logger.info("Overdue reminder email sent successfully to: {}", to);

        } catch (Exception e) {
            logger.error("Failed to send overdue reminder email: {}", e.getMessage());
        }
    }

    // ============= 📧 PAYMENT CONFIRMATION EMAIL =============

    @Override
    public void sendPaymentConfirmationEmail(StudentEntity student, FeesEntity fees, Integer amount, String transactionId) {
        try {
            String to = getStudentEmail(student);
            String subject = "Payment Confirmation - " + schoolName;

            String htmlContent = buildPaymentConfirmationHtml(student, fees, amount, transactionId);
            sendEmail(to, subject, htmlContent);

            logger.info("Payment confirmation email sent successfully to: {}", to);

        } catch (Exception e) {
            logger.error("Failed to send payment confirmation email: {}", e.getMessage());
        }
    }

    // ============= 🎨 HTML TEMPLATE BUILDERS =============

    private String buildFeeReminderHtml(StudentEntity student, FeesEntity fees) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Fee Payment Reminder</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; background-color: #f4f7fc; padding: 20px; }");
        html.append(".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
        html.append(".header { background: #3498db; color: white; padding: 20px; text-align: center; }");
        html.append(".header h1 { margin: 0; font-size: 24px; }");
        html.append(".content { padding: 30px; }");
        html.append(".greeting { font-size: 18px; margin-bottom: 20px; }");
        html.append(".fee-box { background: #f8f9fa; border-left: 4px solid #3498db; padding: 20px; margin: 20px 0; }");
        html.append(".amount { font-size: 24px; color: #e74c3c; font-weight: bold; }");
        html.append(".installment-item { border-bottom: 1px solid #dee2e6; padding: 10px 0; }");
        html.append(".due-badge { background: #fff3cd; color: #856404; padding: 3px 10px; border-radius: 15px; font-size: 12px; }");
        html.append(".overdue-badge { background: #f8d7da; color: #721c24; padding: 3px 10px; border-radius: 15px; font-size: 12px; }");
        html.append(".button { display: inline-block; padding: 12px 30px; background: #3498db; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; margin-top: 20px; }");
        html.append(".button:hover { background: #2980b9; }");
        html.append(".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #6c757d; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");

        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<h1>🏫 ").append(schoolName).append("</h1>");
        html.append("<p>Fee Payment Reminder</p>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<div class='greeting'>Dear ").append(getParentName(student)).append(",</div>");

        html.append("<p>This is a reminder for your child <strong>")
                .append(student.getFirstName()).append(" ").append(student.getLastName())
                .append("</strong> (Class: ").append(student.getCurrentClass()).append("-").append(student.getSection())
                .append(").</p>");

        // Fee Summary
        int paidAmount = fees.getTotalFees() - fees.getRemainingFees();
        html.append("<div class='fee-box'>");
        html.append("<p><strong>Total Fees:</strong> ₹").append(fees.getTotalFees()).append("</p>");
        html.append("<p><strong>Paid Amount:</strong> ₹").append(paidAmount).append("</p>");
        html.append("<p><strong>Pending Amount:</strong> <span class='amount'>₹").append(fees.getRemainingFees()).append("</span></p>");
        html.append("</div>");

        // Installments
        List<Installment> pendingInstallments = fees.getInstallmentsList().stream()
                .filter(i -> !"PAID".equalsIgnoreCase(i.getStatus()))
                .collect(Collectors.toList());

        if (!pendingInstallments.isEmpty()) {
            html.append("<h3>Installment Details:</h3>");

            for (Installment inst : pendingInstallments) {
                boolean isOverdue = isOverdue(inst);
                String badgeClass = isOverdue ? "overdue-badge" : "due-badge";
                String badgeText = isOverdue ? "OVERDUE" : "PENDING";

                html.append("<div class='installment-item'>");
                html.append("<p><strong>Installment #").append(inst.getInstallmentId()).append("</strong> ");
                html.append("<span class='").append(badgeClass).append("'>").append(badgeText).append("</span></p>");
                html.append("<p>Due Date: ").append(formatDate(inst.getDueDate())).append("</p>");
                html.append("<p>Amount: ₹").append(inst.getAmount()).append("</p>");

                // Show late fee separately if overdue (not added to installment amount)
                if (isOverdue) {
                    int daysOverdue = calculateDaysOverdue(inst.getDueDate());
                    int lateFee = daysOverdue * 100;
                    html.append("<p style='color: #e74c3c;'><strong>Late Fee (₹100/day):</strong> +₹").append(lateFee).append("</p>");
                }

                html.append("</div>");
            }
        }

        // Pay Button - redirects to login page
        html.append("<div style='text-align: center;'>");
        html.append("<a href='").append(baseUrl).append("/login.html' class='button'>💰 Click to Pay</a>");
        html.append("</div>");

        html.append("<p style='margin-top: 20px; font-size: 12px; color: #6c757d;'>");
        html.append("Login to your account to view all details and make payment.</p>");

        html.append("</div>");
        html.append("<div class='footer'>");
        html.append("<p>").append(schoolName).append("</p>");
        html.append("<p>This is an automated message. Please do not reply.</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    private String buildInstallmentDueHtml(StudentEntity student, FeesEntity fees, Installment installment) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Installment Due Reminder</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; background-color: #f4f7fc; padding: 20px; }");
        html.append(".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
        html.append(".header { background: #f39c12; color: white; padding: 20px; text-align: center; }");
        html.append(".content { padding: 30px; }");
        html.append(".greeting { font-size: 18px; margin-bottom: 20px; }");
        html.append(".installment-box { background: #fef9e7; border-left: 4px solid #f39c12; padding: 20px; margin: 20px 0; }");
        html.append(".button { display: inline-block; padding: 12px 30px; background: #f39c12; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }");
        html.append(".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #6c757d; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");

        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<h1>⏰ Installment Due Reminder</h1>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<div class='greeting'>Dear ").append(getParentName(student)).append(",</div>");

        html.append("<p>This is a reminder for your child's installment:</p>");

        html.append("<div class='installment-box'>");
        html.append("<p><strong>Student Name:</strong> ").append(student.getFirstName()).append(" ").append(student.getLastName()).append("</p>");
        html.append("<p><strong>Installment #:</strong> ").append(installment.getInstallmentId()).append("</p>");
        html.append("<p><strong>Due Date:</strong> ").append(formatDate(installment.getDueDate())).append("</p>");
        html.append("<p><strong>Amount:</strong> ₹").append(installment.getAmount()).append("</p>");

        // Check if overdue
        if (isOverdue(installment)) {
            int daysOverdue = calculateDaysOverdue(installment.getDueDate());
            int lateFee = daysOverdue * 100;
            html.append("<p style='color: #e74c3c;'><strong>Late Fee:</strong> +₹").append(lateFee).append(" (₹100/day)</p>");
        }

        html.append("</div>");

        // Pay Button - redirects to login page
        html.append("<div style='text-align: center;'>");
        html.append("<a href='").append(baseUrl).append("/login.html' class='button'>💰 View Details</a>");
        html.append("</div>");

        html.append("</div>");
        html.append("<div class='footer'>");
        html.append("<p>").append(schoolName).append("</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    private String buildOverdueReminderHtml(StudentEntity student, FeesEntity fees, Installment installment) {
        StringBuilder html = new StringBuilder();

        int daysOverdue = calculateDaysOverdue(installment.getDueDate());
        int lateFee = daysOverdue * 100;

        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Payment Overdue Reminder</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; background-color: #f4f7fc; padding: 20px; }");
        html.append(".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
        html.append(".header { background: #e74c3c; color: white; padding: 20px; text-align: center; }");
        html.append(".urgent-badge { background: #fff3cd; color: #856404; padding: 5px 15px; border-radius: 20px; display: inline-block; font-weight: bold; margin-bottom: 10px; }");
        html.append(".content { padding: 30px; }");
        html.append(".overdue-box { background: #fef2f2; border-left: 4px solid #e74c3c; padding: 20px; margin: 20px 0; }");
        html.append(".late-fee { color: #e74c3c; font-weight: bold; }");
        html.append(".button { display: inline-block; padding: 12px 30px; background: #e74c3c; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }");
        html.append(".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #6c757d; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");

        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<span class='urgent-badge'>⚠️ URGENT</span>");
        html.append("<h1>Payment Overdue</h1>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<div class='greeting'>Dear ").append(getParentName(student)).append(",</div>");

        html.append("<p>Your payment is overdue. Please pay as soon as possible.</p>");

        html.append("<div class='overdue-box'>");
        html.append("<p><strong>Student Name:</strong> ").append(student.getFirstName()).append(" ").append(student.getLastName()).append("</p>");
        html.append("<p><strong>Installment #:</strong> ").append(installment.getInstallmentId()).append("</p>");
        html.append("<p><strong>Due Date:</strong> ").append(formatDate(installment.getDueDate())).append("</p>");
        html.append("<p><strong>Installment Amount:</strong> ₹").append(installment.getAmount()).append("</p>");
        html.append("<p class='late-fee'><strong>Late Fee:</strong> +₹").append(lateFee).append(" (").append(daysOverdue).append(" days @ ₹100/day)</p>");
        html.append("<p><strong>Total to Pay:</strong> ₹").append(installment.getAmount() + lateFee).append("</p>");
        html.append("</div>");

        // Pay Button - redirects to login page
        html.append("<div style='text-align: center;'>");
        html.append("<a href='").append(baseUrl).append("/login.html' class='button'>💰 Click to Pay</a>");
        html.append("</div>");

        html.append("</div>");
        html.append("<div class='footer'>");
        html.append("<p>").append(schoolName).append("</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    private String buildPaymentConfirmationHtml(StudentEntity student, FeesEntity fees, Integer amount, String transactionId) {
        StringBuilder html = new StringBuilder();

        int remainingFees = fees.getRemainingFees() - amount;
        Installment nextInstallment = fees.getInstallmentsList().stream()
                .filter(i -> !"PAID".equalsIgnoreCase(i.getStatus()))
                .findFirst()
                .orElse(null);

        html.append("<!DOCTYPE html>");
        html.append("<html lang='en'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Payment Confirmation</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; background-color: #f4f7fc; padding: 20px; }");
        html.append(".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }");
        html.append(".header { background: #27ae60; color: white; padding: 20px; text-align: center; }");
        html.append(".success-icon { font-size: 48px; }");
        html.append(".content { padding: 30px; }");
        html.append(".payment-box { background: #f8f9fa; border-radius: 5px; padding: 20px; margin: 20px 0; }");
        html.append(".button { display: inline-block; padding: 12px 30px; background: #27ae60; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; }");
        html.append(".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #6c757d; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");

        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<div class='success-icon'>✅</div>");
        html.append("<h1>Payment Successful!</h1>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<p>Thank you for your payment!</p>");

        html.append("<div class='payment-box'>");
        html.append("<p><strong>Student Name:</strong> ").append(student.getFirstName()).append(" ").append(student.getLastName()).append("</p>");
        html.append("<p><strong>Amount Paid:</strong> ₹").append(amount).append("</p>");
        html.append("<p><strong>Transaction ID:</strong> ").append(transactionId).append("</p>");
        html.append("<p><strong>Date:</strong> ").append(LocalDate.now().format(DATE_FORMATTER)).append("</p>");
        html.append("</div>");

        html.append("<p><strong>Remaining Balance:</strong> ₹").append(Math.max(0, remainingFees)).append("</p>");

        if (nextInstallment != null) {
            html.append("<p><strong>Next Due Date:</strong> ").append(formatDate(nextInstallment.getDueDate())).append("</p>");
        }

        // View Dashboard Button - redirects to login page
        html.append("<div style='text-align: center; margin-top: 30px;'>");
        html.append("<a href='").append(baseUrl).append("/login.html' class='button'>📊 View Dashboard</a>");
        html.append("</div>");

        html.append("</div>");
        html.append("<div class='footer'>");
        html.append("<p>").append(schoolName).append("</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    // ============= 📧 EMAIL SENDER =============

    private void sendEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    // ============= 🛠️ HELPER METHODS =============

    private String getStudentEmail(StudentEntity student) {
        if (student.getFatherEmail() != null && !student.getFatherEmail().isEmpty()) {
            return student.getFatherEmail();
        } else if (student.getMotherEmail() != null && !student.getMotherEmail().isEmpty()) {
            return student.getMotherEmail();
        } else if (student.getGuardianEmail() != null && !student.getGuardianEmail().isEmpty()) {
            return student.getGuardianEmail();
        }
        return student.getFatherEmail();
    }

    private String getParentName(StudentEntity student) {
        if (student.getFatherName() != null && !student.getFatherName().isEmpty()) {
            return student.getFatherName();
        } else if (student.getMotherName() != null && !student.getMotherName().isEmpty()) {
            return student.getMotherName();
        } else if (student.getGuardianName() != null && !student.getGuardianName().isEmpty()) {
            return student.getGuardianName();
        }
        return "Parent";
    }

    private String formatDate(LocalDate date) {
        if (date == null) return "N/A";
        return date.format(DATE_FORMATTER);
    }

    private boolean isOverdue(Installment installment) {
        if ("PAID".equalsIgnoreCase(installment.getStatus())) {
            return false;
        }
        return installment.getDueDate() != null && LocalDate.now().isAfter(installment.getDueDate());
    }

    private int calculateDaysOverdue(LocalDate dueDate) {
        if (dueDate == null) return 0;
        return (int) (LocalDate.now().toEpochDay() - dueDate.toEpochDay());
    }
}
package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.LivestockSalePayment;
import rw.animalproduct.animal.production.entity.Users;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SaleEmailService {

    private static final Logger logger = LoggerFactory.getLogger(SaleEmailService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final JavaMailSender mailSender;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    public SaleEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        logger.info("✅ SaleEmailService initialized");
        logger.info("📧 MailSender: {}", mailSender != null ? "Available" : "NULL");
        logger.info("📧 From address: {}", fromAddress);
        logger.info("📧 Recipients: {}", recipientsCsv);
        logger.info("📧 Notifications enabled: {}", notificationsEnabled);
    }

    // ─── Test Method with Detailed Logging ──────────────────────────────────

    public void sendTestEmail() {
        try {
            logger.info("========================================");
            logger.info("📧 TEST EMAIL - STARTING SEND PROCESS");
            logger.info("========================================");

            // Check if mail sender is available
            if (mailSender == null) {
                logger.error("❌ MailSender is NULL! Check Spring configuration.");
                return;
            }

            // Check recipients
            List<String> recipients = getRecipients();
            if (recipients.isEmpty()) {
                logger.error("❌ No recipients configured! Set app.notifications.recipients");
                return;
            }
            logger.info("📧 Recipients: {}", recipients);

            // Check if notifications are enabled
            if (!notificationsEnabled) {
                logger.warn("⚠️ Notifications are disabled. Set app.notifications.enabled=true");
                return;
            }

            // Build simple HTML email
            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Arial, sans-serif; background: #f0f4f8; padding: 20px;">
                    <div style="max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; padding: 24px; box-shadow: 0 2px 10px rgba(0,0,0,0.08);">
                        <h1 style="color: #059669;">✅ Test Email</h1>
                        <p>This is a test email from the <strong>Animal Production System</strong>.</p>
                        <p>If you received this, your email configuration is working correctly!</p>
                        <hr style="border: 1px solid #e2e8f0; margin: 16px 0;">
                        <p style="color: #94a3b8; font-size: 12px;">Animal Production System | Version 0.1.0</p>
                        <p style="color: #94a3b8; font-size: 12px;">From: <span th:text="${fromAddress}"></span></p>
                    </div>
                </body>
                </html>
            """;

            logger.info("📧 Sending test email to: {}", recipients);

            try {
                sendEmail("✅ Test Email - Animal Production System", html);
                logger.info("✅ Test email sent successfully!");
            } catch (MailSendException mse) {
                logger.error("❌ MailSendException: {}", mse.getMessage());
                // Log each failed message
                if (mse.getFailedMessages() != null) {
                    mse.getFailedMessages().forEach((key, value) -> {
                        logger.error("   Failed message: {} - {}", key, value.getMessage());
                    });
                }
                throw mse;
            }

        } catch (Exception e) {
            logger.error("❌ Test email failed: {}", e.getMessage(), e);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // ─── Sale Created ─────────────────────────────────────────────────────────

    public void sendSaleCreatedNotification(LivestockSale sale, Users creator) {
        if (sale == null) {
            logger.warn("❌ Cannot send sale created notification: sale is null");
            return;
        }

        if (!notificationsEnabled) {
            logger.warn("⚠️ Notifications disabled - skipping email for {}", sale.getSaleReference());
            return;
        }

        try {
            logger.info("📧 Preparing sale created email for {}", sale.getSaleReference());

            String html = buildSaleEmailHtml(sale, creator, "Created", "DRAFT");
            String subject = "📋 New Sale Created: " + sale.getSaleReference();

            sendEmail(subject, html);
            logger.info("✅ Sale creation email sent for {}", sale.getSaleReference());

        } catch (Exception e) {
            logger.error("❌ Failed to send sale creation email: {}", e.getMessage(), e);
        }
    }

    // ─── Sale Confirmed ──────────────────────────────────────────────────────

    public void sendSaleConfirmedNotification(LivestockSale sale, Users confirmer) {
        if (sale == null) {
            logger.warn("❌ Cannot send sale confirmed notification: sale is null");
            return;
        }

        if (!notificationsEnabled) {
            logger.warn("⚠️ Notifications disabled - skipping email for {}", sale.getSaleReference());
            return;
        }

        try {
            logger.info("📧 Preparing sale confirmed email for {}", sale.getSaleReference());

            String html = buildSaleEmailHtml(sale, confirmer, "Confirmed", "CONFIRMED");
            String subject = "✅ Sale Confirmed: " + sale.getSaleReference();

            sendEmail(subject, html);
            logger.info("✅ Sale confirmation email sent for {}", sale.getSaleReference());

        } catch (Exception e) {
            logger.error("❌ Failed to send sale confirmation email: {}", e.getMessage(), e);
        }
    }

    // ─── Payment Recorded ────────────────────────────────────────────────────

    public void sendSalePaymentNotification(LivestockSale sale, LivestockSalePayment payment, Users recorder) {
        if (sale == null || payment == null) {
            logger.warn("❌ Cannot send payment notification: sale or payment is null");
            return;
        }

        if (!notificationsEnabled) {
            logger.warn("⚠️ Notifications disabled - skipping payment email for {}", sale.getSaleReference());
            return;
        }

        try {
            logger.info("📧 Preparing payment email for {}", sale.getSaleReference());

            String html = buildPaymentEmailHtml(sale, payment, recorder);
            String subject = "💰 Payment Recorded for " + sale.getSaleReference();

            sendEmail(subject, html);
            logger.info("✅ Payment email sent for {}", sale.getSaleReference());

        } catch (Exception e) {
            logger.error("❌ Failed to send payment email: {}", e.getMessage(), e);
        }
    }

    // ─── Sale Cancelled ──────────────────────────────────────────────────────

    public void sendSaleCancelledNotification(LivestockSale sale, Users canceller) {
        if (sale == null) {
            logger.warn("❌ Cannot send sale cancelled notification: sale is null");
            return;
        }

        if (!notificationsEnabled) {
            logger.warn("⚠️ Notifications disabled - skipping cancellation email for {}", sale.getSaleReference());
            return;
        }

        try {
            logger.info("📧 Preparing sale cancelled email for {}", sale.getSaleReference());

            String html = buildSaleEmailHtml(sale, canceller, "Cancelled", "CANCELLED");
            String subject = "❌ Sale Cancelled: " + sale.getSaleReference();

            sendEmail(subject, html);
            logger.info("✅ Sale cancellation email sent for {}", sale.getSaleReference());

        } catch (Exception e) {
            logger.error("❌ Failed to send sale cancellation email: {}", e.getMessage(), e);
        }
    }

    // ─── Sale Refunded ──────────────────────────────────────────────────────

    public void sendSaleRefundedNotification(LivestockSale sale, Users refundedBy) {
        if (sale == null) {
            logger.warn("❌ Cannot send sale refunded notification: sale is null");
            return;
        }

        if (!notificationsEnabled) {
            logger.warn("⚠️ Notifications disabled - skipping refund email for {}", sale.getSaleReference());
            return;
        }

        try {
            logger.info("📧 Preparing sale refunded email for {}", sale.getSaleReference());

            String html = buildSaleEmailHtml(sale, refundedBy, "Refunded", "REFUNDED");
            String subject = "↩️ Sale Refunded: " + sale.getSaleReference();

            sendEmail(subject, html);
            logger.info("✅ Sale refund email sent for {}", sale.getSaleReference());

        } catch (Exception e) {
            logger.error("❌ Failed to send sale refund email: {}", e.getMessage(), e);
        }
    }

    // ─── Email Builders ─────────────────────────────────────────────────────

    private String buildSaleEmailHtml(LivestockSale sale, Users user, String action, String status) {
        String userEmail = user != null ? user.getEmail() : "System";
        String saleDate = sale.getSaleDate() != null ? sale.getSaleDate().format(DATE_FORMATTER) : "—";
        String createdAt = sale.getCreatedAt() != null ? sale.getCreatedAt().format(DATE_TIME_FORMATTER) : "—";
        String animalTag = sale.getLivestock() != null ? sale.getLivestock().getTagNumber() : "—";
        String buyerName = sale.getBuyer() != null ? sale.getBuyer().getFullName() : "Not assigned";
        String salePrice = formatMoney(sale.getSalePrice()) + " RWF";
        String amountPaid = formatMoney(sale.getAmountPaid()) + " RWF";
        String balance = formatMoney(sale.getBalance()) + " RWF";
        String paymentStatus = sale.getPaymentStatusLabel();

        String statusColor = switch (status) {
            case "CONFIRMED" -> "#059669";
            case "CANCELLED" -> "#dc2626";
            case "REFUNDED" -> "#d97706";
            default -> "#f59e0b";
        };

        String emoji = switch (status) {
            case "CONFIRMED" -> "✅";
            case "CANCELLED" -> "❌";
            case "REFUNDED" -> "↩️";
            default -> "📋";
        };

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0; padding:0; background:#f0f4f8; font-family:'Segoe UI', Arial, sans-serif;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f0f4f8; padding:20px 0;">
                <tr><td align="center">
                <table role="presentation" width="680" cellpadding="0" cellspacing="0"
                       style="background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 10px rgba(0,0,0,0.08);">
                    <tr>
                        <td style="background:linear-gradient(135deg,#1a5f7a,COLOR_PLACEHOLDER); padding:24px 32px;">
                            <span style="font-size:28px;">EMOJI_PLACEHOLDER</span>
                            <h1 style="margin:8px 0 0; color:#ffffff; font-size:20px; font-weight:700;">Sale ACTION_PLACEHOLDER</h1>
                            <p style="margin:4px 0 0; color:#e2e8f0; font-size:13px;">
                                Sale reference: <strong>SALE_REF</strong>
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:24px 32px;">
                            <p style="margin:0 0 16px; color:#334155; font-size:14px; line-height:1.6;">
                                A sale has been <strong>ACTION_PLACEHOLDER</strong> by <strong>USER_EMAIL</strong>.
                            </p>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td width="50%" valign="top" style="padding-right:8px;">
                                        <table width="100%" cellpadding="0" cellspacing="0"
                                               style="border:1px solid #e2e8f0; border-radius:8px; overflow:hidden;">
                                            <tr style="background:#f8fafc;">
                                                <td colspan="2" style="padding:6px 12px; font-size:11px; font-weight:800; color:#1a5f7a; text-transform:uppercase; letter-spacing:.05em;">
                                                    Sale Information
                                                </td>
                                            </tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b; width:45%;">Reference</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#0f172a;">SALE_REF</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Date</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">SALE_DATE</td></tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Sale Price</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#059669;">SALE_PRICE</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Amount Paid</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">AMOUNT_PAID</td></tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Balance</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#d97706;">BALANCE</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Payment Status</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">PAYMENT_STATUS</td></tr>
                                        </table>
                                    </td>
                                    <td width="50%" valign="top" style="padding-left:8px;">
                                        <table width="100%" cellpadding="0" cellspacing="0"
                                               style="border:1px solid #e2e8f0; border-radius:8px; overflow:hidden;">
                                            <tr style="background:#f8fafc;">
                                                <td colspan="2" style="padding:6px 12px; font-size:11px; font-weight:800; color:#1a5f7a; text-transform:uppercase; letter-spacing:.05em;">
                                                    Animal & Buyer
                                                </td>
                                            </tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b; width:45%;">Animal Tag</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">ANIMAL_TAG</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Buyer</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">BUYER_NAME</td></tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Created At</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">CREATED_AT</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Status</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:COLOR_PLACEHOLDER;">ACTION_PLACEHOLDER</td></tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                            <p style="margin:16px 0 0; font-size:12px; color:#94a3b8;">
                                <strong>Next Step:</strong> ACTION_PLACEHOLDER
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td style="background:#f8fafc; padding:12px 32px; border-top:1px solid #e2e8f0;">
                            <p style="margin:0; font-size:11px; color:#94a3b8;">
                                Animal Production System | Version 0.1.0
                            </p>
                        </td>
                    </tr>
                </table>
                </td></tr>
            </table>
            </body>
            </html>
            """
                .replace("SALE_REF", sale.getSaleReference())
                .replace("SALE_DATE", saleDate)
                .replace("SALE_PRICE", salePrice)
                .replace("AMOUNT_PAID", amountPaid)
                .replace("BALANCE", balance)
                .replace("PAYMENT_STATUS", paymentStatus)
                .replace("ANIMAL_TAG", animalTag)
                .replace("BUYER_NAME", buyerName)
                .replace("CREATED_AT", createdAt)
                .replace("USER_EMAIL", userEmail)
                .replace("ACTION_PLACEHOLDER", action)
                .replace("COLOR_PLACEHOLDER", statusColor)
                .replace("EMOJI_PLACEHOLDER", emoji);
    }

    private String buildPaymentEmailHtml(LivestockSale sale, LivestockSalePayment payment, Users recorder) {
        String userEmail = recorder != null ? recorder.getEmail() : "System";
        String animalTag = sale.getLivestock() != null ? sale.getLivestock().getTagNumber() : "—";
        String buyerName = sale.getBuyer() != null ? sale.getBuyer().getFullName() : "Not assigned";
        String salePrice = formatMoney(sale.getSalePrice()) + " RWF";
        String totalPaid = formatMoney(sale.getAmountPaid()) + " RWF";
        String balance = formatMoney(sale.getBalance()) + " RWF";
        String paymentAmount = formatMoney(payment.getAmount()) + " RWF";
        String paymentMethod = payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "Not specified";
        String paymentStatus = sale.getPaymentStatusLabel();
        String paymentDate = payment.getPaymentDate() != null ? payment.getPaymentDate().format(DATE_TIME_FORMATTER) : "—";

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0; padding:0; background:#f0f4f8; font-family:'Segoe UI', Arial, sans-serif;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f0f4f8; padding:20px 0;">
                <tr><td align="center">
                <table role="presentation" width="680" cellpadding="0" cellspacing="0"
                       style="background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 10px rgba(0,0,0,0.08);">
                    <tr>
                        <td style="background:linear-gradient(135deg,#1a5f7a,#0d9488); padding:24px 32px;">
                            <span style="font-size:28px;">💰</span>
                            <h1 style="margin:8px 0 0; color:#ffffff; font-size:20px; font-weight:700;">Payment Recorded</h1>
                            <p style="margin:4px 0 0; color:#ccfbf1; font-size:13px;">
                                Sale reference: <strong>SALE_REF</strong>
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding:24px 32px;">
                            <div style="background:#ecfdf5; border-radius:8px; padding:12px 16px; margin-bottom:16px; border-left:4px solid #0d9488;">
                                <p style="margin:0; color:#065f46; font-size:14px;">
                                    💰 Payment of <strong>PAYMENT_AMOUNT</strong> recorded by <strong>USER_EMAIL</strong>.
                                    <br>
                                    <span style="font-size:13px;">Status: <strong>PAYMENT_STATUS</strong></span>
                                </p>
                            </div>
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                                <tr>
                                    <td width="50%" valign="top" style="padding-right:8px;">
                                        <table width="100%" cellpadding="0" cellspacing="0"
                                               style="border:1px solid #e2e8f0; border-radius:8px; overflow:hidden;">
                                            <tr style="background:#f8fafc;">
                                                <td colspan="2" style="padding:6px 12px; font-size:11px; font-weight:800; color:#1a5f7a; text-transform:uppercase; letter-spacing:.05em;">
                                                    Payment Details
                                                </td>
                                            </tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b; width:45%;">Amount</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#059669;">PAYMENT_AMOUNT</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Method</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">PAYMENT_METHOD</td></tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Date</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">PAYMENT_DATE</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Recorded By</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">USER_EMAIL</td></tr>
                                        </table>
                                    </td>
                                    <td width="50%" valign="top" style="padding-left:8px;">
                                        <table width="100%" cellpadding="0" cellspacing="0"
                                               style="border:1px solid #e2e8f0; border-radius:8px; overflow:hidden;">
                                            <tr style="background:#f8fafc;">
                                                <td colspan="2" style="padding:6px 12px; font-size:11px; font-weight:800; color:#1a5f7a; text-transform:uppercase; letter-spacing:.05em;">
                                                    Updated Financial Status
                                                </td>
                                            </tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b; width:45%;">Sale Price</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">SALE_PRICE</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Total Paid</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#059669;">TOTAL_PAID</td></tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Balance</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#d97706;">BALANCE</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Status</td>
                                                <td style="padding:8px 12px; font-size:13px; font-weight:700; color:#0d9488;">PAYMENT_STATUS</td></tr>
                                        </table>
                                        <table width="100%" cellpadding="0" cellspacing="0"
                                               style="border:1px solid #e2e8f0; border-radius:8px; overflow:hidden; margin-top:8px;">
                                            <tr style="background:#f8fafc;">
                                                <td colspan="2" style="padding:6px 12px; font-size:11px; font-weight:800; color:#1a5f7a; text-transform:uppercase; letter-spacing:.05em;">
                                                    Sale Reference
                                                </td>
                                            </tr>
                                            <tr><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b; width:45%;">Animal</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">ANIMAL_TAG</td></tr>
                                            <tr style="background:#f8fafc;"><td style="padding:8px 12px; font-size:11px; font-weight:700; color:#64748b;">Buyer</td>
                                                <td style="padding:8px 12px; font-size:13px; color:#0f172a;">BUYER_NAME</td></tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td style="background:#f8fafc; padding:12px 32px; border-top:1px solid #e2e8f0;">
                            <p style="margin:0; font-size:11px; color:#94a3b8;">
                                Animal Production System | Version 0.1.0
                            </p>
                        </td>
                    </tr>
                </table>
                </td></tr>
            </table>
            </body>
            </html>
            """
                .replace("SALE_REF", sale.getSaleReference())
                .replace("SALE_PRICE", salePrice)
                .replace("TOTAL_PAID", totalPaid)
                .replace("BALANCE", balance)
                .replace("PAYMENT_AMOUNT", paymentAmount)
                .replace("PAYMENT_METHOD", paymentMethod)
                .replace("PAYMENT_DATE", paymentDate)
                .replace("PAYMENT_STATUS", paymentStatus)
                .replace("ANIMAL_TAG", animalTag)
                .replace("BUYER_NAME", buyerName)
                .replace("USER_EMAIL", userEmail);
    }

    // ─── Email Sender ──────────────────────────────────────────────────────

    private void sendEmail(String subject, String htmlContent) {
        List<String> recipients = getRecipients();

        if (recipients.isEmpty()) {
            logger.warn("⚠️ No notification recipients configured - skipping email: {}", subject);
            return;
        }

        try {
            logger.info("📧 Sending email: '{}' to {}", subject, String.join(", ", recipients));

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            logger.info("✅ Email sent successfully: {}", subject);

        } catch (Exception e) {
            logger.error("❌ Failed to send email '{}': {}", subject, e.getMessage(), e);
            // Print full stack trace for debugging
            e.printStackTrace();
        }
    }

    private List<String> getRecipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) {
            logger.warn("⚠️ No recipients configured. Set app.notifications.recipients in application.properties");
            return List.of();
        }
        return Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString()
                .replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
    }
}
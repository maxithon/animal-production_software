package rw.animalproduct.animal.production.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import rw.animalproduct.animal.production.dto.OtherExpenseSummary;
import rw.animalproduct.animal.production.entity.OtherExpense;
import rw.animalproduct.animal.production.patches.AsyncConfig;
import rw.animalproduct.animal.production.repository.OtherExpenseRepository;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * ✅ CHANGED: unified onto the same app.notifications.* property keys used by
 * LifecycleEmailService, SickAnimalNotificationService, and
 * EmailDiagnosticController — instead of its own app.notification.email.*
 * keys with no defaults, which crashed the whole application at startup the
 * moment those old keys were removed from application.properties. Every
 * @Value below now has a safe default, and sending goes to the full
 * recipients list instead of a single hardcoded "to" address.
 */
@Service
public class OtherExpenseService {

    private static final Logger log = LoggerFactory.getLogger(OtherExpenseService.class);

    private final OtherExpenseRepository otherExpenseRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // Same shared background executor LivestockController uses for
    // registration emails (confirmed working in this app, unlike @Async).
    private final Executor notificationExecutor;

    @Value("${app.notifications.recipients:}")
    private String recipientsCsv;

    @Value("${app.notifications.from:no-reply@animalproduction.rw}")
    private String fromAddress;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationEnabled;

    public OtherExpenseService(OtherExpenseRepository otherExpenseRepository,
                               JavaMailSender mailSender,
                               TemplateEngine templateEngine,
                               @Qualifier(AsyncConfig.NOTIFICATION_EXECUTOR) Executor notificationExecutor) {
        this.otherExpenseRepository = otherExpenseRepository;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.notificationExecutor = notificationExecutor;
    }

    // ── Reads ──────────────────────────────────────────────────────────

    public List<OtherExpenseSummary> getAllSummaries() {
        return otherExpenseRepository.findAllSummaries();
    }

    public List<OtherExpenseSummary> search(String query) {
        if (!StringUtils.hasText(query)) {
            return otherExpenseRepository.findAllSummaries();
        }
        return otherExpenseRepository.searchSummaries(query.trim());
    }

    public Optional<OtherExpense> getById(UUID id) {
        return otherExpenseRepository.findById(id).filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()));
    }

    // ── KPIs ───────────────────────────────────────────────────────────

    public BigDecimal totalThisMonth() {
        YearMonth ym = YearMonth.now();
        return otherExpenseRepository.sumBetween(ym.atDay(1), ym.atEndOfMonth());
    }

    public BigDecimal totalThisYear() {
        LocalDate start = LocalDate.now().withDayOfYear(1);
        LocalDate end = LocalDate.now();
        return otherExpenseRepository.sumBetween(start, end);
    }

    public BigDecimal totalAllTime() {
        return otherExpenseRepository.sumAll();
    }

    public long countAll() {
        return otherExpenseRepository.countAll();
    }

    public List<OtherExpenseRepository.CategoryTotal> totalsByCategory() {
        return otherExpenseRepository.sumByCategory();
    }

    // ── Report (filterable by date range + category) ─────────────────

    public List<OtherExpenseSummary> getReportData(LocalDate start, LocalDate end, String category) {
        return otherExpenseRepository.findFiltered(start, end, category);
    }

    public BigDecimal getReportTotal(LocalDate start, LocalDate end, String category) {
        return otherExpenseRepository.sumFiltered(start, end, category);
    }

    public List<OtherExpenseRepository.CategoryTotal> getReportCategoryBreakdown(LocalDate start, LocalDate end) {
        return otherExpenseRepository.sumByCategoryBetween(start, end);
    }

    public List<OtherExpenseRepository.MonthlyTotal> getReportMonthlyTrend(LocalDate start, LocalDate end) {
        return otherExpenseRepository.sumByMonthBetween(start, end);
    }

    public List<String> getDistinctCategories() {
        return otherExpenseRepository.findDistinctCategories();
    }

    // ── Create ─────────────────────────────────────────────────────────

    @Transactional
    public OtherExpense addNew(OtherExpense expense) {
        OtherExpense saved = otherExpenseRepository.save(expense);
        otherExpenseRepository.flush();

        // Fire-and-forget on the shared notification executor — same pattern
        // as LivestockController.register() / LivestockService.create().
        // Runs off the request thread and never rolls back or fails the save.
        notificationExecutor.execute(() -> {
            try {
                sendOtherExpenseRecordedEmail(saved);
            } catch (Exception emailEx) {
                log.warn("Other-expense email failed to send for '{}' (id={}): {}",
                        saved.getDescription(), saved.getId(), emailEx.getMessage(), emailEx);
            }
        });

        return saved;
    }

    // ── Update ─────────────────────────────────────────────────────────

    @Transactional
    public OtherExpense update(UUID id, OtherExpense updated) {
        OtherExpense existing = otherExpenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found with ID: " + id));

        existing.setExpenseDate(updated.getExpenseDate());
        existing.setCategory(updated.getCategory());
        existing.setDescription(updated.getDescription());
        existing.setAmount(updated.getAmount());
        existing.setPaymentMethod(updated.getPaymentMethod());
        existing.setPaidTo(updated.getPaidTo());
        existing.setReceiptReference(updated.getReceiptReference());
        existing.setNotes(updated.getNotes());

        OtherExpense saved = otherExpenseRepository.save(existing);
        otherExpenseRepository.flush();
        return saved;
    }

    // ── Soft delete ────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        OtherExpense existing = otherExpenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found with ID: " + id));
        existing.setIsDeleted(true);
        otherExpenseRepository.save(existing);
        otherExpenseRepository.flush();
    }

    // ── Notification email ────────────────────────────────────────────
    //
    // Moved here from the now-deleted OtherExpenseNotificationService so
    // the whole create-and-notify flow lives in one place, mirroring
    // LivestockService. Deliberately NOT @Async — threading is handled by
    // the caller (addNew()) via the shared notificationExecutor, which is
    // the pattern confirmed to actually send mail in this app.

    private void sendOtherExpenseRecordedEmail(OtherExpense expense) {
        if (!notificationEnabled || expense == null) {
            return;
        }

        List<String> recipients = recipients();
        if (recipients.isEmpty()) {
            log.warn("No notification recipients configured (app.notifications.recipients) — skipping other-expense email");
            return;
        }

        try {
            Context ctx = new Context(LocaleContextHolder.getLocale());
            ctx.setVariable("title", "Other Expense Recorded");
            ctx.setVariable("emoji", "💸📋");

            ctx.setVariable("expenseDate", expense.getExpenseDate() != null
                    ? expense.getExpenseDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "—");

            ctx.setVariable("category", expense.getCategory() != null && !expense.getCategory().isBlank()
                    ? expense.getCategory().replace("_", " ") : "—");

            ctx.setVariable("description", expense.getDescription() != null
                    && !expense.getDescription().isBlank() ? expense.getDescription() : "—");

            ctx.setVariable("amount", expense.getAmount() != null
                    ? NumberFormat.getNumberInstance(Locale.US).format(expense.getAmount()) : "0");

            ctx.setVariable("paymentMethod", expense.getPaymentMethod() != null
                    && !expense.getPaymentMethod().isBlank() ? expense.getPaymentMethod() : "Not recorded");

            ctx.setVariable("paidTo", expense.getPaidTo() != null && !expense.getPaidTo().isBlank()
                    ? expense.getPaidTo() : "Not recorded");

            ctx.setVariable("receiptReference", expense.getReceiptReference() != null
                    && !expense.getReceiptReference().isBlank() ? expense.getReceiptReference() : "Not recorded");

            ctx.setVariable("notes", expense.getNotes() != null && !expense.getNotes().isBlank()
                    ? expense.getNotes() : "Not recorded");

            ctx.setVariable("createdByUsername", expense.getCreatedByUsername() != null
                    ? expense.getCreatedByUsername() : "—");

            ctx.setVariable("createdAt", expense.getCreatedAt() != null
                    ? expense.getCreatedAt().toString() : "—");

            ctx.setVariable("today", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            String html = templateEngine.process("emails/email-other-expense-recorded", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject("💸 Other Expense Recorded — "
                    + (expense.getCategory() != null ? expense.getCategory().replace("_", " ") : "Expense"));
            helper.setText(html, true);

            mailSender.send(message);

            log.info("✅ Other-expense email sent to {} recipient(s) for '{}' (id={})",
                    recipients.size(), expense.getDescription(), expense.getId());
        } catch (MessagingException e) {
            // An email hiccup must never roll back or fail the expense save.
            log.error("❌ Failed to send other-expense email: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending other-expense email: {}", e.getMessage(), e);
        }
    }

    private List<String> recipients() {
        if (recipientsCsv == null || recipientsCsv.isBlank()) return List.of();
        return java.util.Arrays.stream(recipientsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
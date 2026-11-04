package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockSalePaymentRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockSaleService {

    private static final Logger logger = LoggerFactory.getLogger(LivestockSaleService.class);

    private final LivestockSaleRepository saleRepository;
    private final LivestockSalePaymentRepository paymentRepository;
    private final LivestockRepository livestockRepository;
    private final BuyerService buyerService;
    private final UsersRepository usersRepository;
    private final SaleEmailService saleEmailService;

    public LivestockSaleService(LivestockSaleRepository saleRepository,
                                LivestockSalePaymentRepository paymentRepository,
                                LivestockRepository livestockRepository,
                                BuyerService buyerService,
                                UsersRepository usersRepository,
                                SaleEmailService saleEmailService) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.livestockRepository = livestockRepository;
        this.buyerService = buyerService;
        this.usersRepository = usersRepository;
        this.saleEmailService = saleEmailService;

        logger.info("✅ LivestockSaleService initialized with email service");
    }

    // ─── Public Methods ──────────────────────────────────────────────────────

    public List<LivestockSale> getAll() {
        return saleRepository.findAll();
    }

    public Optional<LivestockSale> getById(UUID id) {
        return saleRepository.findById(id);
    }

    public List<LivestockSale> getByLivestock(UUID livestockId) {
        return saleRepository.findByLivestockId(livestockId);
    }

    public List<LivestockSalePayment> getPayments(UUID saleId) {
        return paymentRepository.findPaymentsBySaleId(saleId);
    }

    /**
     * Create a new sale in DRAFT status
     */
    @Transactional
    public LivestockSale addNew(LivestockSale sale) {
        logger.info("📝 Creating new sale for animal: {}", sale.getLivestockIdValue());

        resolveAndSetLivestock(sale);
        resolveAndSetBuyer(sale);
        resolveAndSetCreatedBy(sale);

        // Validate sale date is not in future
        if (sale.getSaleDate() != null && sale.getSaleDate().isAfter(LocalDate.now())) {
            throw new RuntimeException("Sale date cannot be in the future.");
        }

        // Validate amountPaid doesn't exceed salePrice
        validatePaymentAmount(sale);

        // Generate reference if not provided
        if (sale.getSaleReference() == null || sale.getSaleReference().isEmpty()) {
            sale.setSaleReference(generateUniqueReference());
        }

        // Initialize amountPaid if null
        if (sale.getAmountPaid() == null) {
            sale.setAmountPaid(BigDecimal.ZERO);
        }

        // Set as DRAFT initially
        sale.setSaleStatus(LivestockSale.SALE_DRAFT);
        sale.setCreatedAt(LocalDateTime.now());

        LivestockSale saved = saleRepository.save(sale);
        logger.info("✅ Sale created successfully: {}", saved.getSaleReference());

        // ─── SEND EMAIL NOTIFICATION ──────────────────────────────────────────
        try {
            Users creator = saved.getCreatedBy();
            saleEmailService.sendSaleCreatedNotification(saved, creator);
            logger.info("📧 Sale creation email sent for {}", saved.getSaleReference());
        } catch (Exception e) {
            logger.error("❌ Failed to send sale created email for {}: {}", saved.getSaleReference(), e.getMessage());
        }

        return saved;
    }

    /**
     * Confirm a sale - changes status from DRAFT to CONFIRMED
     * Marks animal as SOLD
     */
    @Transactional
    public LivestockSale confirmSale(UUID id, UUID confirmedBy, String notes) {
        logger.info("✅ Confirming sale with ID: {}", id);

        LivestockSale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + id));

        if (!sale.isConfirmable()) {
            throw new RuntimeException("Sale is not in DRAFT status. Current status: " + sale.getSaleStatus());
        }

        // Check for duplicate active sale on the same animal
        UUID livestockId = sale.getLivestock().getId();
        List<LivestockSale> existingSales = saleRepository.findByLivestockId(livestockId);
        for (LivestockSale existing : existingSales) {
            if (!existing.getId().equals(id) &&
                    LivestockSale.SALE_CONFIRMED.equals(existing.getSaleStatus())) {
                throw new RuntimeException(
                        "Animal " + sale.getLivestock().getTagNumber() +
                                " already has an active confirmed sale. Cannot confirm another."
                );
            }
        }

        // Set status to CONFIRMED
        sale.setSaleStatus(LivestockSale.SALE_CONFIRMED);
        sale.setConfirmedAt(LocalDateTime.now());
        if (confirmedBy != null) {
            usersRepository.findById(confirmedBy).ifPresent(sale::setConfirmedBy);
        }
        if (notes != null) {
            sale.setNotes((sale.getNotes() != null ? sale.getNotes() + "\n" : "") + "Confirmed: " + notes);
        }

        // Mark animal as SOLD (Animal Status Integration)
        if (sale.getLivestock() != null) {
            Livestock animal = sale.getLivestock();
            if (!Livestock.STATUS_SOLD.equals(animal.getStatus())) {
                animal.setStatus(Livestock.STATUS_SOLD);
                livestockRepository.save(animal);
                logger.info("✅ Animal {} marked as SOLD", animal.getTagNumber());
            }
        }

        LivestockSale saved = saleRepository.save(sale);
        logger.info("✅ Sale confirmed: {}", saved.getSaleReference());

        // ─── SEND EMAIL NOTIFICATION ──────────────────────────────────────────
        try {
            Users confirmer = saved.getConfirmedBy();
            saleEmailService.sendSaleConfirmedNotification(saved, confirmer);
            logger.info("📧 Sale confirmation email sent for {}", saved.getSaleReference());
        } catch (Exception e) {
            logger.error("❌ Failed to send sale confirmed email for {}: {}", saved.getSaleReference(), e.getMessage());
        }

        return saved;
    }

    /**
     * Update a draft sale
     */
    @Transactional
    public LivestockSale update(UUID id, LivestockSale updated) {
        logger.info("✏️ Updating sale with ID: {}", id);

        LivestockSale existing = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + id));

        // Only DRAFT sales can be edited
        if (!existing.isEditable()) {
            throw new RuntimeException("Cannot edit a " + existing.getSaleStatus() + " sale.");
        }

        // Validate sale date is not in future
        if (updated.getSaleDate() != null && updated.getSaleDate().isAfter(LocalDate.now())) {
            throw new RuntimeException("Sale date cannot be in the future.");
        }

        // Validate amountPaid doesn't exceed salePrice
        validatePaymentAmount(updated);

        // Update fields
        existing.setSaleReason(updated.getSaleReason());
        existing.setSalePrice(updated.getSalePrice());
        existing.setSaleDate(updated.getSaleDate());
        existing.setSaleLocation(updated.getSaleLocation());
        existing.setAcquisitionCost(updated.getAcquisitionCost());
        existing.setAmountPaid(updated.getAmountPaid());
        existing.setPaymentMethod(updated.getPaymentMethod());
        existing.setPaymentReference(updated.getPaymentReference());
        existing.setNotes(updated.getNotes());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        existing.setBuyerIdValue(updated.getBuyerIdValue());

        resolveAndSetLivestock(existing);
        resolveAndSetBuyer(existing);

        // Sale remains DRAFT
        LivestockSale saved = saleRepository.save(existing);
        logger.info("✅ Sale updated: {}", saved.getSaleReference());

        return saved;
    }

    /**
     * Cancel a sale - requires reason and user
     * Restores animal status to ACTIVE
     */
    @Transactional
    public LivestockSale cancelSale(UUID id, UUID cancelledBy, String reason) {
        logger.info("❌ Cancelling sale with ID: {}", id);

        LivestockSale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + id));

        if (!sale.isCancellable()) {
            throw new RuntimeException("Sale is already " + sale.getSaleStatus() + " and cannot be cancelled.");
        }

        // Set status to CANCELLED
        sale.setSaleStatus(LivestockSale.SALE_CANCELLED);
        sale.setCancelledAt(LocalDateTime.now());
        if (cancelledBy != null) {
            usersRepository.findById(cancelledBy).ifPresent(sale::setCancelledBy);
        }
        if (reason != null && !reason.trim().isEmpty()) {
            sale.setCancellationReason(reason);
            sale.setNotes((sale.getNotes() != null ? sale.getNotes() + "\n" : "") +
                    "Cancellation reason: " + reason);
        }

        // Restore animal status to ACTIVE
        if (sale.getLivestock() != null) {
            Livestock animal = sale.getLivestock();
            animal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(animal);
            logger.info("🔄 Animal {} restored to ACTIVE", animal.getTagNumber());
        }

        LivestockSale saved = saleRepository.save(sale);
        logger.info("✅ Sale cancelled: {}", saved.getSaleReference());

        // ─── SEND EMAIL NOTIFICATION ──────────────────────────────────────────
        try {
            Users canceller = saved.getCancelledBy();
            saleEmailService.sendSaleCancelledNotification(saved, canceller);
            logger.info("📧 Sale cancellation email sent for {}", saved.getSaleReference());
        } catch (Exception e) {
            logger.error("❌ Failed to send cancellation email for {}: {}", saved.getSaleReference(), e.getMessage());
        }

        return saved;
    }

    /**
     * Refund a sale - requires reason and user
     * Records refund amount
     * Restores animal status to ACTIVE
     */
    @Transactional
    public LivestockSale refundSale(UUID id, UUID refundedBy, BigDecimal refundAmount, String reason) {
        logger.info("↩️ Refunding sale with ID: {}", id);

        LivestockSale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + id));

        if (!sale.isRefundable()) {
            throw new RuntimeException(
                    "Sale is not refundable. Status: " + sale.getSaleStatus() +
                            ", Payment: " + sale.getPaymentStatus()
            );
        }

        // Validate refund amount
        BigDecimal maxRefund = sale.getAmountPaid() != null ? sale.getAmountPaid() : BigDecimal.ZERO;
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Refund amount must be greater than zero.");
        }
        if (refundAmount.compareTo(maxRefund) > 0) {
            throw new RuntimeException(
                    "Refund amount (" + refundAmount + ") exceeds amount paid (" + maxRefund + ")."
            );
        }

        // Set status to REFUNDED
        sale.setSaleStatus(LivestockSale.SALE_REFUNDED);
        sale.setRefundedAt(LocalDateTime.now());
        if (refundedBy != null) {
            usersRepository.findById(refundedBy).ifPresent(sale::setRefundedBy);
        }
        sale.setRefundAmount(refundAmount);
        sale.setRefundReason(reason);
        if (reason != null && !reason.trim().isEmpty()) {
            sale.setNotes((sale.getNotes() != null ? sale.getNotes() + "\n" : "") +
                    "Refund reason: " + reason);
        }

        // Restore animal status to ACTIVE
        if (sale.getLivestock() != null) {
            Livestock animal = sale.getLivestock();
            animal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(animal);
            logger.info("🔄 Animal {} restored to ACTIVE", animal.getTagNumber());
        }

        LivestockSale saved = saleRepository.save(sale);
        logger.info("✅ Sale refunded: {}", saved.getSaleReference());

        // ─── SEND EMAIL NOTIFICATION ──────────────────────────────────────────
        try {
            Users refundedByUser = saved.getRefundedBy();
            saleEmailService.sendSaleRefundedNotification(saved, refundedByUser);
            logger.info("📧 Sale refund email sent for {}", saved.getSaleReference());
        } catch (Exception e) {
            logger.error("❌ Failed to send refund email for {}: {}", saved.getSaleReference(), e.getMessage());
        }

        return saved;
    }

    /**
     * Record a payment - separate function with full payment history
     */
    @Transactional
    public LivestockSale recordPayment(UUID saleId, BigDecimal paymentAmount, UUID recordedBy,
                                       String paymentMethod, String paymentReference, String notes) {
        logger.info("💰 Recording payment of {} for sale ID: {}", paymentAmount, saleId);

        LivestockSale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + saleId));

        if (!LivestockSale.SALE_CONFIRMED.equals(sale.getSaleStatus())) {
            throw new RuntimeException("Cannot record payment for a " + sale.getSaleStatus() + " sale.");
        }

        if (LivestockSale.SALE_CANCELLED.equals(sale.getSaleStatus()) ||
                LivestockSale.SALE_REFUNDED.equals(sale.getSaleStatus())) {
            throw new RuntimeException("Cannot record payment for a " + sale.getSaleStatus() + " sale.");
        }

        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero.");
        }

        BigDecimal currentPaid = sale.getAmountPaid() != null ? sale.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(paymentAmount);
        BigDecimal balance = sale.getBalance();

        if (paymentAmount.compareTo(balance) > 0) {
            throw new RuntimeException(
                    "Payment amount (" + paymentAmount +
                            ") exceeds remaining balance (" + balance + ")"
            );
        }

        // Create payment record
        LivestockSalePayment payment = new LivestockSalePayment();
        payment.setSale(sale);
        payment.setAmount(paymentAmount);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentMethod(paymentMethod != null ? paymentMethod : sale.getPaymentMethod());
        payment.setPaymentReference(paymentReference != null ? paymentReference : sale.getPaymentReference());
        payment.setNotes(notes);
        if (recordedBy != null) {
            usersRepository.findById(recordedBy).ifPresent(payment::setRecordedBy);
        }
        LivestockSalePayment savedPayment = paymentRepository.save(payment);
        logger.info("✅ Payment record created: {} RWF", paymentAmount);

        // Update sale amount paid
        sale.setAmountPaid(newPaid);

        LivestockSale updatedSale = saleRepository.save(sale);
        logger.info("✅ Sale {} updated with new payment. Total paid: {}", updatedSale.getSaleReference(), newPaid);

        // ─── SEND EMAIL NOTIFICATION ──────────────────────────────────────────
        try {
            Users recorder = savedPayment.getRecordedBy();
            saleEmailService.sendSalePaymentNotification(updatedSale, savedPayment, recorder);
            logger.info("📧 Payment email sent for {}", updatedSale.getSaleReference());
        } catch (Exception e) {
            logger.error("❌ Failed to send payment email for {}: {}", updatedSale.getSaleReference(), e.getMessage());
        }

        return updatedSale;
    }

    /**
     * Delete a sale (soft delete) - only for DRAFT sales
     */
    @Transactional
    public void delete(UUID id) {
        logger.info("🗑️ Deleting sale with ID: {}", id);

        LivestockSale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with ID: " + id));

        // Only DRAFT sales can be deleted
        if (!sale.isEditable()) {
            throw new RuntimeException("Cannot delete a " + sale.getSaleStatus() + " sale.");
        }

        // Restore animal status if it was marked SOLD
        if (sale.getLivestock() != null) {
            Livestock animal = sale.getLivestock();
            if (Livestock.STATUS_SOLD.equals(animal.getStatus())) {
                // Check if there's another active sale for this animal
                List<LivestockSale> sales = saleRepository.findByLivestockId(animal.getId());
                boolean hasOtherActiveSale = sales.stream()
                        .filter(s -> !s.getId().equals(id))
                        .anyMatch(s -> LivestockSale.SALE_CONFIRMED.equals(s.getSaleStatus()));
                if (!hasOtherActiveSale) {
                    animal.setStatus(Livestock.STATUS_ACTIVE);
                    livestockRepository.save(animal);
                    logger.info("🔄 Animal {} restored to ACTIVE", animal.getTagNumber());
                }
            }
        }

        sale.setIsDeleted(true);
        saleRepository.save(sale);
        logger.info("✅ Sale soft deleted: {}", sale.getSaleReference());
    }

    // ─── Private Helper Methods ────────────────────────────────────────────

    private void validatePaymentAmount(LivestockSale sale) {
        if (sale.getSalePrice() != null && sale.getAmountPaid() != null) {
            if (sale.getAmountPaid().compareTo(sale.getSalePrice()) > 0) {
                throw new RuntimeException(
                        "Amount paid (" + sale.getAmountPaid() +
                                ") cannot exceed sale price (" + sale.getSalePrice() + ")"
                );
            }
        }
    }

    private String generateUniqueReference() {
        String base = "SAL-" + LocalDate.now().getYear() + "-";
        String reference;
        int counter = 1;
        do {
            reference = base + String.format("%04d", counter);
            counter++;
        } while (saleRepository.findBySaleReference(reference) != null);
        return reference;
    }

    private void resolveAndSetLivestock(LivestockSale sale) {
        String idStr = sale.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException(
                            "Livestock not found with ID: " + idStr));
            sale.setLivestock(ls);
            // Set acquisition cost from animal
            if (ls.getCurrentValue() != null) {
                sale.setAcquisitionCost(ls.getCurrentValue());
            }
        }
    }

    private void resolveAndSetBuyer(LivestockSale sale) {
        String buyerIdStr = sale.getBuyerIdValue();
        if (buyerIdStr != null && !buyerIdStr.isEmpty()) {
            Buyer buyer = buyerService.getById(UUID.fromString(buyerIdStr))
                    .orElseThrow(() -> new RuntimeException(
                            "Buyer not found with ID: " + buyerIdStr));
            sale.setBuyer(buyer);
        }
    }

    private void resolveAndSetCreatedBy(LivestockSale sale) {
        String createdByIdStr = sale.getCreatedByIdValue();
        if (createdByIdStr != null && !createdByIdStr.isEmpty()) {
            Users user = usersRepository.findById(UUID.fromString(createdByIdStr))
                    .orElse(null);
            sale.setCreatedBy(user);
        }
    }
}
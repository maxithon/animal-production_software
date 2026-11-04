package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single source of truth for animal tag allocation across the entire system.
 *
 * <p>Both the manual registration form (via LivestockController.suggestTag) and
 * the bulk importer call this service. That guarantees numbering is consistent
 * regardless of entry point.</p>
 *
 * <p>Format: <code>{CATEGORY_CODE}-{N}</code>, N is zero-padded to 3 digits.</p>
 */
@Service
public class TagAllocationService {

    private static final Logger log = LoggerFactory.getLogger(TagAllocationService.class);

    private static final String DEFAULT_PREFIX = "TAG";
    private static final int    PAD_WIDTH      = 3;
    private static final int    MAX_ATTEMPTS   = 1_000_000;

    private final LivestockRepository         livestockRepository;
    private final LivestockCategoryRepository categoryRepository;

    public TagAllocationService(LivestockRepository livestockRepository,
                                LivestockCategoryRepository categoryRepository) {
        this.livestockRepository  = livestockRepository;
        this.categoryRepository   = categoryRepository;
    }

    // ════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════════

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public String nextTagFor(LivestockCategory category, Set<String> reserved) {
        if (category == null) return null;
        String prefix = resolvePrefix(category);
        int max = highestExistingNumber(prefix);

        if (reserved != null) {
            for (String t : reserved) {
                int n = parseNumber(prefix, t);
                if (n > max) max = n;
            }
        }

        int candidate = max + 1;
        String tag;
        int attempts = 0;
        do {
            tag = String.format("%s-%0" + PAD_WIDTH + "d", prefix, candidate++);
            attempts++;
            if (attempts > MAX_ATTEMPTS) {
                log.error("[tag-alloc] exceeded {} attempts for prefix '{}'",
                        MAX_ATTEMPTS, prefix);
                throw new IllegalStateException(
                        "Could not allocate a unique tag for prefix " + prefix);
            }
        } while ((reserved != null && reserved.contains(tag))
                || livestockRepository.existsByTagNumber(tag));
        return tag;
    }

    public String nextTagFor(LivestockCategory category) {
        return nextTagFor(category, null);
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public TagSuggestion suggest(LivestockCategory category) {
        if (category == null) {
            return new TagSuggestion(null, null);
        }
        String prefix = resolvePrefix(category);
        int max = highestExistingNumber(prefix);
        String lastTag = max > 0
                ? String.format("%s-%0" + PAD_WIDTH + "d", prefix, max)
                : null;
        return new TagSuggestion(lastTag,
                String.format("%s-%0" + PAD_WIDTH + "d", prefix, max + 1));
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNALS
    // ════════════════════════════════════════════════════════════════════════

    private String resolvePrefix(LivestockCategory category) {
        String code = category.getCode();
        return (code == null || code.isBlank())
                ? DEFAULT_PREFIX
                : code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Tolerant pattern: {@code "GOAT-001"}, {@code "goat-1"}, {@code "GOAT - 001"},
     * {@code "GOAT-001 "} (trailing whitespace), all match.
     */
    private Pattern patternFor(String prefix) {
        return Pattern.compile(
                "^\\s*" + Pattern.quote(prefix) + "\\s*-\\s*(\\d+)\\s*$",
                Pattern.CASE_INSENSITIVE);
    }

    /**
     * Computes the highest existing numeric suffix for a given prefix.
     *
     * ✅ FIX: soft-delete has been removed from Livestock, so rows no longer
     * carry an is_deleted flag — deleted animals are physically removed from
     * the table. That means findAll() only ever returns live rows, and the
     * max number is automatically correct without any extra filtering.
     */
    private int highestExistingNumber(String prefix) {
        Pattern p = patternFor(prefix);
        int max = 0;

        List<Livestock> all = livestockRepository.findAll();
        for (Livestock a : all) {
            String tag = a.getTagNumber();
            if (tag == null) continue;

            Matcher m = p.matcher(tag);
            if (!m.matches()) continue;
            try {
                int n = Integer.parseInt(m.group(1));
                if (n > max) max = n;
            } catch (NumberFormatException ignore) { }
        }
        return max;
    }

    private int parseNumber(String prefix, String tag) {
        if (tag == null) return 0;
        Matcher m = patternFor(prefix).matcher(tag);
        if (!m.matches()) return 0;
        try { return Integer.parseInt(m.group(1)); }
        catch (NumberFormatException e) { return 0; }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DTO
    // ════════════════════════════════════════════════════════════════════════

    public static class TagSuggestion {
        private final String lastTag;
        private final String suggestedTag;

        public TagSuggestion(String lastTag, String suggestedTag) {
            this.lastTag      = lastTag;
            this.suggestedTag = suggestedTag;
        }
        public String getLastTag()      { return lastTag; }
        public String getSuggestedTag() { return suggestedTag; }
    }
}
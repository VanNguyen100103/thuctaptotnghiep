package com.ut.edu.backend.tax;

import com.ut.edu.backend.exception.ResourceNotFoundException;
import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The Thuế & Kế toán module's one piece of reasoning: turning a store's
 * invoices into tờ khai 01/CNKD, period by period.
 *
 * Everything here is derived except three facts the shop owns - who the
 * taxpayer is (TaxProfile), which periods it has filed, and what the figures
 * were when it filed them (TaxDeclaration). That split is why a store can
 * open this module for the first time and immediately see correct quarters
 * for the whole year: there is nothing to seed, and nothing to keep in sync
 * with the sales that produced the numbers.
 */
@Service
@RequiredArgsConstructor
public class TaxDeclarationService {

    /** Tax is filed in whole đồng - the form has no decimals, and a stray fraction is a rejected return. */
    private static final int MONEY_SCALE = 0;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** How far back the year picker reaches when the shop has filed nothing yet. */
    private static final int YEAR_PICKER_LOOKBACK = 2;

    private final TaxProfileRepository profileRepository;
    private final TaxDeclarationRepository declarationRepository;
    private final TaxRevenueService revenueService;
    private final TenantGuard tenantGuard;

    // ------------------------------------------------------------------
    // Thiết lập
    // ------------------------------------------------------------------

    /**
     * The store's tax profile, created on the spot the first time anything
     * asks for it.
     *
     * Lazily rather than at store registration, because nearly every field
     * can only be filled by someone holding the household's business licence
     * - a row written at sign-up would be an empty one that the onboarding
     * code has to remember to create, and every store that registered before
     * this module existed would be missing it anyway.
     */
    @Transactional
    public TaxProfile getOrCreateProfile(Long storeId) {
        return profileRepository.findByStoreId(storeId).orElseGet(() -> {
            try {
                return profileRepository.saveAndFlush(TaxProfile.builder()
                        .store(tenantGuard.currentStoreRef())
                        .build());
            } catch (DataIntegrityViolationException e) {
                // Opening the module loads the rail and the declaration list
                // at the same moment, and on a store with no profile yet both
                // requests race to insert one. uk_tax_profiles_store is what
                // stops the second row; this is what stops the second request
                // failing over it.
                return profileRepository.findByStoreId(storeId).orElseThrow(() -> e);
            }
        });
    }

    @Transactional
    public TaxProfile saveProfile(Long storeId, TaxProfileRequest request) {
        TaxProfile profile = getOrCreateProfile(storeId);
        profile.setBusinessName(trimToNull(request.businessName()));
        profile.setTaxCode(trimToNull(request.taxCode()));
        profile.setOwnerName(trimToNull(request.ownerName()));
        profile.setBusinessAddress(trimToNull(request.businessAddress()));
        profile.setTaxOffice(trimToNull(request.taxOffice()));
        if (request.taxMethod() != null) {
            profile.setTaxMethod(request.taxMethod());
        }
        if (request.periodType() != null) {
            profile.setPeriodType(request.periodType());
        }
        if (request.activity() != null) {
            profile.setActivity(request.activity());
        }
        if (request.exemptThreshold() != null) {
            profile.setExemptThreshold(request.exemptThreshold());
        }
        return profileRepository.save(profile);
    }

    // ------------------------------------------------------------------
    // Tờ khai 01/CNKD
    // ------------------------------------------------------------------

    @Transactional
    public TaxYearSummaryResponse yearSummary(Long storeId, int year, LocalDate today) {
        TaxProfile profile = getOrCreateProfile(storeId);
        TaxPeriodType periodType = profile.getPeriodType();

        Map<Integer, TaxDeclaration> stored = storedByPeriodNumber(storeId, year, periodType);
        List<TaxPeriod> periods = TaxPeriod.ofYear(year, periodType, today);

        List<TaxDeclarationRowResponse> rows = new ArrayList<>();
        BigDecimal cumulativeRevenue = BigDecimal.ZERO;
        BigDecimal cumulativeVat = BigDecimal.ZERO;
        BigDecimal cumulativePit = BigDecimal.ZERO;

        for (TaxPeriod period : periods) {
            TaxDeclaration declaration = stored.get(period.number());
            Figures figures = figuresFor(storeId, period, profile, declaration);
            cumulativeRevenue = cumulativeRevenue.add(figures.revenue());
            cumulativeVat = cumulativeVat.add(figures.vat());
            cumulativePit = cumulativePit.add(figures.pit());
            rows.add(toRow(period, declaration, figures, today));
        }

        // Newest period first, the way the list shows it - Quý 2 sits above
        // Quý 1 - while the running totals above were accumulated
        // oldest-first, the only order a "luỹ kế" figure is right in.
        rows.sort(Comparator.comparingInt(TaxDeclarationRowResponse::periodNumber).reversed());

        BigDecimal threshold = profile.getExemptThreshold();
        boolean underThreshold = threshold != null
                && threshold.signum() > 0
                && cumulativeRevenue.compareTo(threshold) <= 0;

        TaxActivity activity = profile.getActivity();
        return new TaxYearSummaryResponse(
                year,
                periodType,
                profile.getTaxMethod(),
                activity,
                activity.getLabel(),
                activity.getVatRate(),
                activity.getPitRate(),
                cumulativeRevenue,
                cumulativeVat,
                cumulativeRevenue,
                cumulativePit,
                threshold,
                underThreshold,
                availableYears(storeId, today),
                rows);
    }

    @Transactional
    public TaxDeclarationDetailResponse detail(Long storeId, int year, int periodNumber, LocalDate today) {
        TaxProfile profile = getOrCreateProfile(storeId);
        TaxPeriodType periodType = profile.getPeriodType();
        TaxPeriod period = requireValidPeriod(year, periodType, periodNumber, today);

        TaxDeclaration declaration = findDeclaration(storeId, period).orElse(null);
        Figures figures = figuresFor(storeId, period, profile, declaration);
        TaxActivity declaredUnder = figures.activity();

        // The paper form's four rows, with the shop's registered activity
        // carrying the whole figure and the other three at zero - see
        // TaxProfile#activity for why revenue is not split per line yet.
        List<TaxActivityLineResponse> lines = new ArrayList<>();
        for (TaxActivity candidate : TaxActivity.values()) {
            boolean isDeclared = candidate == declaredUnder;
            lines.add(new TaxActivityLineResponse(
                    candidate,
                    candidate.getLabel(),
                    isDeclared ? figures.revenue() : BigDecimal.ZERO,
                    candidate.getVatRate(),
                    isDeclared ? figures.vat() : BigDecimal.ZERO,
                    candidate.getPitRate(),
                    isDeclared ? figures.pit() : BigDecimal.ZERO));
        }

        // A filed period still shows its working, but read from the live
        // tills: what is useful there is the comparison ("we filed 45,8tr,
        // the books now say 46,1tr"), not a second copy of the snapshot.
        TaxRevenueService.RevenueBreakdown breakdown = figures.frozen()
                ? revenueService.forPeriod(storeId, period)
                : figures.breakdown();

        return new TaxDeclarationDetailResponse(
                year,
                periodType,
                periodNumber,
                period.label(),
                period.startDate(),
                period.endDate(),
                period.hasClosed(today) ? period.dueDate() : null,
                overdueDays(period, declaration, today),
                statusOf(period, declaration, today),
                declaration == null ? 1 : declaration.getDeclarationRound(),
                declaration == null ? null : declaration.getSubmittedAt(),
                declaration == null ? null : declaration.getNote(),
                TaxProfileResponse.of(profile),
                lines,
                figures.revenue(),
                figures.vat(),
                figures.pit(),
                figures.totalTax(),
                breakdown.posRevenue(),
                breakdown.posCount(),
                breakdown.onlineRevenue(),
                breakdown.onlineCount(),
                figures.frozen());
    }

    /**
     * "Đã nộp" and its undo, from the status dropdown on the list.
     *
     * Marking filed snapshots the figures; un-marking bumps the round, so the
     * next filing of that period is a "Bổ sung lần 2", which is what the tax
     * office calls it. A period that has not closed cannot be filed at all -
     * the figures would still be moving under it.
     */
    @Transactional
    public TaxDeclarationRowResponse setSubmitted(Long storeId, int year, int periodNumber,
                                                  boolean submitted, User actor, LocalDate today) {
        TaxProfile profile = getOrCreateProfile(storeId);
        TaxPeriodType periodType = profile.getPeriodType();
        TaxPeriod period = requireValidPeriod(year, periodType, periodNumber, today);

        if (submitted && !period.hasClosed(today)) {
            throw new IllegalArgumentException(
                    "Chưa thể nộp tờ khai " + period.label() + "/" + year + " khi kỳ kê khai chưa kết thúc");
        }

        TaxDeclaration declaration = findDeclaration(storeId, period).orElseGet(() -> TaxDeclaration.builder()
                .store(tenantGuard.currentStoreRef())
                .periodYear(year)
                .periodType(periodType)
                .periodNumber(periodNumber)
                .build());

        if (submitted) {
            BigDecimal revenue = revenueService.forPeriod(storeId, period).total();
            TaxActivity activity = profile.getActivity();
            declaration.setTaxableRevenue(revenue);
            declaration.setVatAmount(taxOn(revenue, activity.getVatRate()));
            declaration.setPitAmount(taxOn(revenue, activity.getPitRate()));
            declaration.setActivity(activity);
            declaration.setSubmittedAt(LocalDateTime.now());
            declaration.setSubmittedBy(actor);
        } else if (!declaration.isSubmitted()) {
            // Nothing to remember: the shop set "Chưa nộp" on a period that
            // was never filed. Writing a row here would fill the table with
            // entries that record no decision at all.
            return toRow(period, declaration.getId() == null ? null : declaration,
                    figuresFor(storeId, period, profile, declaration), today);
        } else {
            declaration.setSubmittedAt(null);
            declaration.setSubmittedBy(null);
            declaration.setTaxableRevenue(null);
            declaration.setVatAmount(null);
            declaration.setPitAmount(null);
            declaration.setActivity(null);
            declaration.setDeclarationRound(declaration.getDeclarationRound() + 1);
        }

        TaxDeclaration saved = declarationRepository.save(declaration);
        return toRow(period, saved, figuresFor(storeId, period, profile, saved), today);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /**
     * One period's money, from whichever source is authoritative for it: the
     * snapshot on a filed return, the live tills otherwise.
     */
    private record Figures(BigDecimal revenue, BigDecimal vat, BigDecimal pit,
                           TaxActivity activity, boolean frozen,
                           TaxRevenueService.RevenueBreakdown breakdown) {

        BigDecimal totalTax() {
            return vat.add(pit);
        }
    }

    private Figures figuresFor(Long storeId, TaxPeriod period, TaxProfile profile, TaxDeclaration declaration) {
        if (declaration != null && declaration.isSubmitted() && declaration.getTaxableRevenue() != null) {
            return new Figures(
                    declaration.getTaxableRevenue(),
                    nz(declaration.getVatAmount()),
                    nz(declaration.getPitAmount()),
                    declaration.getActivity() == null ? profile.getActivity() : declaration.getActivity(),
                    true,
                    TaxRevenueService.RevenueBreakdown.empty());
        }
        TaxRevenueService.RevenueBreakdown breakdown = revenueService.forPeriod(storeId, period);
        BigDecimal revenue = breakdown.total();
        TaxActivity activity = profile.getActivity();
        return new Figures(
                revenue,
                taxOn(revenue, activity.getVatRate()),
                taxOn(revenue, activity.getPitRate()),
                activity,
                false,
                breakdown);
    }

    /** revenue x rate%, in whole đồng. */
    private static BigDecimal taxOn(BigDecimal revenue, BigDecimal ratePercent) {
        return revenue.multiply(ratePercent).divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private TaxDeclarationRowResponse toRow(TaxPeriod period, TaxDeclaration declaration,
                                            Figures figures, LocalDate today) {
        return new TaxDeclarationRowResponse(
                period.year(),
                period.type(),
                period.number(),
                period.label(),
                period.startDate(),
                period.endDate(),
                period.hasClosed(today) ? period.dueDate() : null,
                overdueDays(period, declaration, today),
                statusOf(period, declaration, today),
                declaration == null ? 1 : declaration.getDeclarationRound(),
                declaration == null ? null : declaration.getSubmittedAt(),
                figures.revenue(),
                figures.vat(),
                figures.pit(),
                figures.totalTax());
    }

    private static TaxDeclarationStatus statusOf(TaxPeriod period, TaxDeclaration declaration, LocalDate today) {
        if (declaration != null && declaration.isSubmitted()) {
            return TaxDeclarationStatus.DA_NOP;
        }
        return period.hasClosed(today) ? TaxDeclarationStatus.CHUA_NOP : TaxDeclarationStatus.DANG_CAP_NHAT;
    }

    /** A filed return is never late, however long ago the deadline was. */
    private static long overdueDays(TaxPeriod period, TaxDeclaration declaration, LocalDate today) {
        if (declaration != null && declaration.isSubmitted()) {
            return 0;
        }
        return period.overdueDays(today);
    }

    private Map<Integer, TaxDeclaration> storedByPeriodNumber(Long storeId, int year, TaxPeriodType periodType) {
        Map<Integer, TaxDeclaration> byNumber = new LinkedHashMap<>();
        declarationRepository.findByStoreIdAndPeriodYearAndPeriodType(storeId, year, periodType)
                .forEach(d -> byNumber.put(d.getPeriodNumber(), d));
        return byNumber;
    }

    private Optional<TaxDeclaration> findDeclaration(Long storeId, TaxPeriod period) {
        return declarationRepository.findByStoreIdAndPeriodYearAndPeriodTypeAndPeriodNumber(
                storeId, period.year(), period.type(), period.number());
    }

    /**
     * The years the picker offers: the current one, the two before it, and
     * any year the shop has already filed something in - so a store filing
     * late for 2023 can still reach that year, while a fresh store gets a
     * short list rather than a scroll back to 1970.
     */
    private List<Integer> availableYears(Long storeId, LocalDate today) {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        for (int offset = 0; offset <= YEAR_PICKER_LOOKBACK; offset++) {
            years.add(today.getYear() - offset);
        }
        declarationRepository.findByStoreIdOrderByPeriodYearDesc(storeId)
                .forEach(d -> years.add(d.getPeriodYear()));
        return new ArrayList<>(years);
    }

    private static TaxPeriod requireValidPeriod(int year, TaxPeriodType type, int number, LocalDate today) {
        int max = type == TaxPeriodType.QUARTER ? 4 : 12;
        if (number < 1 || number > max) {
            throw new IllegalArgumentException("Kỳ kê khai không hợp lệ: " + number);
        }
        TaxPeriod period = new TaxPeriod(year, type, number);
        if (!period.hasStarted(today)) {
            throw new ResourceNotFoundException("Kỳ kê khai " + period.label() + "/" + year + " chưa bắt đầu");
        }
        return period;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

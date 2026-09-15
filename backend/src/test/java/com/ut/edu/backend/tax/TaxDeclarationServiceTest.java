package com.ut.edu.backend.tax;

import com.ut.edu.backend.store.TenantGuard;
import com.ut.edu.backend.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The money rules of tờ khai 01/CNKD: the rates that come out of the chosen
 * ngành nghề, what "luỹ kế" accumulates, and the one behaviour a shop would
 * otherwise discover the hard way - that a filed quarter keeps the figures it
 * was filed with, whatever the books do afterwards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxDeclarationServiceTest {

    private static final Long STORE_ID = 7L;

    /** Mid-September 2026: Q1 and Q2 are closed and overdue, Q3 is still open. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    @Mock private TaxProfileRepository profileRepository;
    @Mock private TaxDeclarationRepository declarationRepository;
    @Mock private TaxRevenueService revenueService;
    @Mock private TenantGuard tenantGuard;

    @InjectMocks
    private TaxDeclarationService service;

    private TaxProfile profile;

    @BeforeEach
    void setUp() {
        profile = TaxProfile.builder()
                .activity(TaxActivity.DISTRIBUTION)
                .taxMethod(TaxMethod.KE_KHAI)
                .periodType(TaxPeriodType.QUARTER)
                .exemptThreshold(TaxProfile.DEFAULT_EXEMPT_THRESHOLD)
                .build();
        when(profileRepository.findByStoreId(STORE_ID)).thenReturn(Optional.of(profile));
        when(declarationRepository.findByStoreIdAndPeriodYearAndPeriodType(anyLong(), anyInt(), any()))
                .thenReturn(List.of());
        when(declarationRepository.findByStoreIdOrderByPeriodYearDesc(anyLong())).thenReturn(List.of());
        when(declarationRepository.save(any(TaxDeclaration.class))).thenAnswer(i -> i.getArgument(0));
        revenueIs(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void revenueIs(BigDecimal pos, BigDecimal online) {
        when(revenueService.forPeriod(anyLong(), any()))
                .thenReturn(new TaxRevenueService.RevenueBreakdown(pos, 1, online, 1));
    }

    @Test
    void distributionRevenueIsTaxedAtOneAndAHalfPercentSplitBetweenTheTwoTaxes() {
        revenueIs(new BigDecimal("40000000"), new BigDecimal("5802250"));

        TaxYearSummaryResponse summary = service.yearSummary(STORE_ID, 2026, TODAY);
        TaxDeclarationRowResponse q1 = summary.periods().stream()
                .filter(p -> p.periodNumber() == 1).findFirst().orElseThrow();

        // 45.802.250 x 1% GTGT and x 0,5% TNCN, rounded to whole đồng.
        assertThat(q1.taxableRevenue()).isEqualByComparingTo("45802250");
        assertThat(q1.vatAmount()).isEqualByComparingTo("458023");
        assertThat(q1.pitAmount()).isEqualByComparingTo("229011");
        assertThat(q1.totalTax()).isEqualByComparingTo("687034");
    }

    @Test
    void theActivityChosenInThietLapPicksTheRates() {
        profile.setActivity(TaxActivity.SERVICE);
        revenueIs(new BigDecimal("10000000"), BigDecimal.ZERO);

        TaxDeclarationRowResponse q1 = service.yearSummary(STORE_ID, 2026, TODAY).periods().stream()
                .filter(p -> p.periodNumber() == 1).findFirst().orElseThrow();

        // Dịch vụ: 5% GTGT / 2% TNCN.
        assertThat(q1.vatAmount()).isEqualByComparingTo("500000");
        assertThat(q1.pitAmount()).isEqualByComparingTo("200000");
    }

    @Test
    void theYearShowsOnlyThePeriodsThatHaveBegun_newestFirst() {
        TaxYearSummaryResponse summary = service.yearSummary(STORE_ID, 2026, TODAY);

        assertThat(summary.periods()).extracting(TaxDeclarationRowResponse::periodNumber)
                .containsExactly(3, 2, 1);
    }

    @Test
    void anOpenPeriodHasNoDeadlineAndIsDangCapNhat() {
        TaxYearSummaryResponse summary = service.yearSummary(STORE_ID, 2026, TODAY);
        TaxDeclarationRowResponse q3 = summary.periods().get(0);

        assertThat(q3.periodNumber()).isEqualTo(3);
        assertThat(q3.status()).isEqualTo(TaxDeclarationStatus.DANG_CAP_NHAT);
        assertThat(q3.dueDate()).isNull();
        assertThat(q3.overdueDays()).isZero();
    }

    @Test
    void aClosedUnfiledPeriodIsChuaNopAndCountsItsOverdueDays() {
        TaxDeclarationRowResponse q1 = service.yearSummary(STORE_ID, 2026, TODAY).periods().stream()
                .filter(p -> p.periodNumber() == 1).findFirst().orElseThrow();

        assertThat(q1.status()).isEqualTo(TaxDeclarationStatus.CHUA_NOP);
        assertThat(q1.dueDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(q1.overdueDays()).isEqualTo(138);
    }

    @Test
    void cumulativeCardsSumEveryPeriodOfTheYear() {
        revenueIs(new BigDecimal("10000000"), BigDecimal.ZERO);

        TaxYearSummaryResponse summary = service.yearSummary(STORE_ID, 2026, TODAY);

        // Three periods have begun, each at 10 triệu.
        assertThat(summary.cumulativeVatRevenue()).isEqualByComparingTo("30000000");
        assertThat(summary.cumulativeVatAmount()).isEqualByComparingTo("300000");
        assertThat(summary.cumulativePitAmount()).isEqualByComparingTo("150000");
        // Both taxes are assessed on the same revenue base under one activity.
        assertThat(summary.cumulativePitRevenue()).isEqualByComparingTo(summary.cumulativeVatRevenue());
    }

    @Test
    void theExemptionNoticeFiresOnlyWhileTheYearIsUnderTheThreshold() {
        revenueIs(new BigDecimal("10000000"), BigDecimal.ZERO);
        assertThat(service.yearSummary(STORE_ID, 2026, TODAY).underExemptThreshold()).isTrue();

        revenueIs(new BigDecimal("80000000"), BigDecimal.ZERO);
        assertThat(service.yearSummary(STORE_ID, 2026, TODAY).underExemptThreshold()).isFalse();
    }

    @Test
    void aZeroThresholdTurnsTheExemptionNoticeOff() {
        profile.setExemptThreshold(BigDecimal.ZERO);
        revenueIs(new BigDecimal("1000"), BigDecimal.ZERO);

        assertThat(service.yearSummary(STORE_ID, 2026, TODAY).underExemptThreshold()).isFalse();
    }

    @Test
    void filingSnapshotsTheFiguresAndLaterSalesDoNotRestateThem() {
        revenueIs(new BigDecimal("45802250"), BigDecimal.ZERO);
        when(declarationRepository.findByStoreIdAndPeriodYearAndPeriodTypeAndPeriodNumber(
                STORE_ID, 2026, TaxPeriodType.QUARTER, 1)).thenReturn(Optional.empty());

        TaxDeclarationRowResponse filed = service.setSubmitted(STORE_ID, 2026, 1, true, new User(), TODAY);

        assertThat(filed.status()).isEqualTo(TaxDeclarationStatus.DA_NOP);
        assertThat(filed.taxableRevenue()).isEqualByComparingTo("45802250");

        // The books move afterwards - a correction, a late invoice - and the
        // filed quarter must not follow them.
        TaxDeclaration stored = TaxDeclaration.builder()
                .periodYear(2026).periodType(TaxPeriodType.QUARTER).periodNumber(1)
                .declarationRound(1)
                .submittedAt(LocalDateTime.now())
                .taxableRevenue(new BigDecimal("45802250"))
                .vatAmount(new BigDecimal("458023"))
                .pitAmount(new BigDecimal("229011"))
                .activity(TaxActivity.DISTRIBUTION)
                .build();
        when(declarationRepository.findByStoreIdAndPeriodYearAndPeriodType(STORE_ID, 2026, TaxPeriodType.QUARTER))
                .thenReturn(List.of(stored));
        revenueIs(new BigDecimal("99999999"), BigDecimal.ZERO);

        TaxDeclarationRowResponse reread = service.yearSummary(STORE_ID, 2026, TODAY).periods().stream()
                .filter(p -> p.periodNumber() == 1).findFirst().orElseThrow();

        assertThat(reread.taxableRevenue()).isEqualByComparingTo("45802250");
        assertThat(reread.vatAmount()).isEqualByComparingTo("458023");
    }

    @Test
    void aFiledPeriodIsNeverLateHoweverLongAgoTheDeadlineWas() {
        TaxDeclaration stored = TaxDeclaration.builder()
                .periodYear(2026).periodType(TaxPeriodType.QUARTER).periodNumber(1)
                .declarationRound(1)
                .submittedAt(LocalDateTime.now())
                .taxableRevenue(BigDecimal.ZERO).vatAmount(BigDecimal.ZERO).pitAmount(BigDecimal.ZERO)
                .activity(TaxActivity.DISTRIBUTION)
                .build();
        when(declarationRepository.findByStoreIdAndPeriodYearAndPeriodType(STORE_ID, 2026, TaxPeriodType.QUARTER))
                .thenReturn(List.of(stored));

        TaxDeclarationRowResponse q1 = service.yearSummary(STORE_ID, 2026, TODAY).periods().stream()
                .filter(p -> p.periodNumber() == 1).findFirst().orElseThrow();

        assertThat(q1.overdueDays()).isZero();
    }

    @Test
    void unfilingClearsTheSnapshotAndMakesTheNextFilingABoSung() {
        TaxDeclaration stored = TaxDeclaration.builder()
                .periodYear(2026).periodType(TaxPeriodType.QUARTER).periodNumber(1)
                .declarationRound(1)
                .submittedAt(LocalDateTime.now())
                .taxableRevenue(new BigDecimal("45802250"))
                .vatAmount(new BigDecimal("458023"))
                .pitAmount(new BigDecimal("229011"))
                .activity(TaxActivity.DISTRIBUTION)
                .build();
        when(declarationRepository.findByStoreIdAndPeriodYearAndPeriodTypeAndPeriodNumber(
                STORE_ID, 2026, TaxPeriodType.QUARTER, 1)).thenReturn(Optional.of(stored));

        TaxDeclarationRowResponse row = service.setSubmitted(STORE_ID, 2026, 1, false, new User(), TODAY);

        assertThat(row.status()).isEqualTo(TaxDeclarationStatus.CHUA_NOP);
        assertThat(row.declarationRound()).isEqualTo(2);
        assertThat(stored.getTaxableRevenue()).isNull();
    }

    @Test
    void anOpenPeriodCannotBeFiled() {
        when(declarationRepository.findByStoreIdAndPeriodYearAndPeriodTypeAndPeriodNumber(
                STORE_ID, 2026, TaxPeriodType.QUARTER, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setSubmitted(STORE_ID, 2026, 3, true, new User(), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chưa kết thúc");
    }

    @Test
    void theDetailDrawsAllFourFormRowsWithOnlyTheRegisteredOneFilled() {
        revenueIs(new BigDecimal("45802250"), BigDecimal.ZERO);

        TaxDeclarationDetailResponse detail = service.detail(STORE_ID, 2026, 1, TODAY);

        assertThat(detail.activityLines()).hasSize(4);
        assertThat(detail.activityLines()).filteredOn(l -> l.revenue().signum() > 0)
                .extracting(TaxActivityLineResponse::activity)
                .containsExactly(TaxActivity.DISTRIBUTION);
        assertThat(detail.taxableRevenue()).isEqualByComparingTo("45802250");
        assertThat(detail.frozen()).isFalse();
    }

    @Test
    void aPeriodThatHasNotBegunIsNotFound() {
        assertThatThrownBy(() -> service.detail(STORE_ID, 2026, 4, TODAY))
                .hasMessageContaining("chưa bắt đầu");
    }
}

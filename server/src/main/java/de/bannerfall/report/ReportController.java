package de.bannerfall.report;

import de.bannerfall.account.AccountPrincipal;
import de.bannerfall.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The player's own reports (04-api <i>Reports</i>). Someone else's report is 404 {@code
 * REPORT_NOT_FOUND}, like one that doesn't exist.
 */
@RestController
@RequestMapping("/api/reports")
class ReportController {

    /** Page sizes above this are refused (400). */
    static final int MAX_PAGE_SIZE = 100;

    /** Keeps {@code page × size} far away from the int range of the database offset. */
    static final int MAX_PAGE = 10_000;

    private final ReportService reportService;
    private final ReportShareService shareService;
    private final PlayerService playerService;

    ReportController(
            ReportService reportService,
            ReportShareService shareService,
            PlayerService playerService) {
        this.reportService = reportService;
        this.shareService = shareService;
        this.playerService = playerService;
    }

    @GetMapping
    ReportPageDto list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) ReportFilter filter,
            @AuthenticationPrincipal AccountPrincipal me) {
        return reportService.list(playerService.idOf(me.accountId()), page, size, filter);
    }

    /** How many of the player's reports are unread (the counter in the navigation). */
    @GetMapping("/unread")
    UnreadReportsDto unread(@AuthenticationPrincipal AccountPrincipal me) {
        return new UnreadReportsDto(reportService.unreadCount(playerService.idOf(me.accountId())));
    }

    /** The report with its payload; marks it read. */
    @GetMapping("/{reportId}")
    ReportDto open(@PathVariable long reportId, @AuthenticationPrincipal AccountPrincipal me) {
        return reportService.open(reportId, playerService.idOf(me.accountId()));
    }

    @DeleteMapping("/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long reportId, @AuthenticationPrincipal AccountPrincipal me) {
        reportService.delete(reportId, playerService.idOf(me.accountId()));
    }

    /**
     * Deletes the reports the player chose in the list's select mode (1.3); ids that aren't his
     * reports (any more) are skipped.
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSome(
            @Valid @RequestBody ReportDeleteRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        reportService.deleteSome(playerService.idOf(me.accountId()), body.ids());
    }

    /**
     * Deletes all of the player's reports up to {@code upTo}, the {@code newestId} of the list the
     * player saw (M38): a report that arrived since stays.
     */
    @PostMapping("/delete-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAll(@RequestParam @Min(0) long upTo, @AuthenticationPrincipal AccountPrincipal me) {
        reportService.deleteAll(playerService.idOf(me.accountId()), upTo);
    }

    /**
     * Deletes the player's own attack and scout reports on barbarian villages up to {@code upTo},
     * except those with a new banner (1.2.3, 02-game-rules §14).
     */
    @PostMapping("/delete-barbarian")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBarbarian(
            @RequestParam @Min(0) long upTo, @AuthenticationPrincipal AccountPrincipal me) {
        reportService.deleteBarbarian(playerService.idOf(me.accountId()), upTo);
    }

    /** Sends a copy of the report to other players by name (M40, 02-game-rules §14). */
    @PostMapping("/{reportId}/share")
    ReportShareDto share(
            @PathVariable long reportId,
            @Valid @RequestBody ReportShareRequest body,
            @AuthenticationPrincipal AccountPrincipal me) {
        return new ReportShareDto(
                shareService.share(reportId, playerService.idOf(me.accountId()), body.names()));
    }

    /**
     * Sends a copy of the report to every other member of the player's guild (02-game-rules §14);
     * skips the members a named send would refuse.
     */
    @PostMapping("/{reportId}/share-guild")
    ReportShareDto shareWithGuild(
            @PathVariable long reportId, @AuthenticationPrincipal AccountPrincipal me) {
        return new ReportShareDto(
                shareService.shareWithGuild(reportId, playerService.idOf(me.accountId())));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readAll(@AuthenticationPrincipal AccountPrincipal me) {
        reportService.markAllRead(playerService.idOf(me.accountId()));
    }
}

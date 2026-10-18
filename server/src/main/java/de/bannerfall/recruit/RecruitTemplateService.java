package de.bannerfall.recruit;

import de.bannerfall.common.ApiException;
import de.bannerfall.common.ErrorCode;
import de.bannerfall.common.NotFoundException;
import de.bannerfall.gamedata.UnitType;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.RecruitRules;
import de.bannerfall.unit.UnitCounts;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recruit templates (02-game-rules §5.2b, 1.4): up to five per player, each a name and the army a
 * village should have. They only feed the recruit page's plan; recruiting goes through the normal
 * orders, so a template can't get around any rule.
 */
@Service
public class RecruitTemplateService {

    /** Longest template name: a button on a phone. */
    public static final int MAX_NAME_LENGTH = 16;

    private final RecruitTemplateRepository templates;
    private final PlayerRepository players;

    RecruitTemplateService(RecruitTemplateRepository templates, PlayerRepository players) {
        this.templates = templates;
        this.players = players;
    }

    /** The player's templates, A to E as far as he has made them. */
    @Transactional(readOnly = true)
    public List<RecruitTemplateDto> templates(long playerId) {
        return templates.findByPlayerIdOrderByLetterAsc(playerId).stream()
                .map(RecruitTemplateDto::of)
                .toList();
    }

    /**
     * Makes or changes one of the player's templates.
     *
     * @throws ApiException 400 {@code VALIDATION} for a count outside 0 … 30 000 or no unit at all
     */
    @Transactional
    public RecruitTemplateDto save(
            long playerId,
            RecruitTemplateLetter letter,
            String name,
            Map<UnitType, Integer> units) {
        UnitCounts counts = checked(units);
        // two saves of a new letter at once would both insert: the player row serialises them
        players.lockById(playerId);
        RecruitTemplate template = templates.findByPlayerIdAndLetter(playerId, letter).orElse(null);
        if (template == null) {
            template = templates.save(new RecruitTemplate(playerId, letter, name.trim(), counts));
        } else {
            template.change(name.trim(), counts);
        }
        return RecruitTemplateDto.of(template);
    }

    /**
     * Deletes one of the player's templates.
     *
     * @throws NotFoundException {@code RECRUIT_TEMPLATE_NOT_FOUND}
     */
    @Transactional
    public void delete(long playerId, RecruitTemplateLetter letter) {
        RecruitTemplate template =
                templates
                        .findByPlayerIdAndLetter(playerId, letter)
                        .orElseThrow(
                                () -> new NotFoundException(ErrorCode.RECRUIT_TEMPLATE_NOT_FOUND));
        templates.delete(template);
    }

    /**
     * The goal as counts: 0 … 30 000 per type (one order's limit) and at least one unit. Every unit
     * type may be part of it: nobles, the paladin and banner thieves too, since the template only
     * says what the village should have.
     */
    private static UnitCounts checked(Map<UnitType, Integer> units) {
        UnitCounts counts = new UnitCounts();
        for (Map.Entry<UnitType, Integer> entry : units.entrySet()) {
            int count = entry.getValue();
            if (count < 0 || count > RecruitRules.MAX_COUNT) {
                throw invalid("Each count is 0 … " + RecruitRules.MAX_COUNT);
            }
            counts.set(entry.getKey(), count);
        }
        if (counts.isEmpty()) {
            throw invalid("The template needs at least one unit");
        }
        return counts;
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, detail);
    }
}

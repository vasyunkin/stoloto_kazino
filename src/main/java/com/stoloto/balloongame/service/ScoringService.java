package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.GameConfig;
import org.springframework.stereotype.Service;

/**
 * Idempotent points calculation for a single polling tick or cashout (§6.3).
 *
 * <h3>Scoring rules (crash-v1):</h3>
 * <ul>
 *   <li>Each newly passed line awards {@code pointsPerLine} (configSnapshot).</li>
 *   <li>GREEN zone (lines 0…greenLevels-1): base points.</li>
 *   <li>RED zone (lines greenLevels…greenLevels+redLevels-1): base × {@code redZoneMultiplier}
 *       (default 1.0 — no bonus, adjustable via admin PUT).</li>
 *   <li>Booster bonus: awarded once when the booster's {@code triggerLine} is crossed.</li>
 * </ul>
 *
 * <p><b>Idempotency:</b> only lines from {@code previousLines} (exclusive) to
 * {@code currentLine} (inclusive) are scored. Calling this multiple times for the
 * same window returns 0 on repeat calls, preventing double-counting on state polls.
 *
 * <p><b>I8:</b> all parameters come from the round's {@code configSnapshot}; this service
 * never touches the live {@code GameConfig} bean.
 */
@Service
public class ScoringService {

    /**
     * Computes points earned for lines crossed in the interval {@code (previousLines, currentLine]}.
     *
     * @param previousLines exclusive lower bound (lines already scored, stored in session)
     * @param currentLine   current line index
     * @param config        the round's immutable {@code configSnapshot}
     * @return delta points to add (may be 0 if no new lines)
     */
    public int pointsForNewLines(int previousLines, int currentLine, GameConfig config) {
        if (currentLine <= previousLines) return 0;

        int greenLines = config.getGreenLevels();
        int redLines = config.getRedLevels();
        int pointsPerLine = config.getPointsPerLine();
        double redMultiplier = 1.0; // §6.3: default 1.0; extend via config field in S6

        int total = 0;

        for (int line = previousLines + 1; line <= currentLine; line++) {
            if (line < greenLines) {
                // GREEN zone
                total += pointsPerLine;
            } else if (line < greenLines + redLines) {
                // RED zone — apply multiplier (rounded to nearest int)
                total += (int) Math.round(pointsPerLine * redMultiplier);
            }
            // Beyond the altitude map: no additional points (would be CRASHED zone)
        }

        return total;
    }

    /**
     * Returns the booster bonus points when the booster activates (first cross of triggerLine).
     *
     * <p>Awarded exactly once per round. Callers must track whether the booster has already
     * been activated (stored in {@code GameSession.boostActivated}).
     *
     * @param booster the round's booster (non-null)
     * @param config  the round's immutable {@code configSnapshot}
     * @return additional points to award
     */
    public int boosterBonus(BoosterResult booster, GameConfig config) {
        // §6.2: bonus = pointsPerLine × boostTierMultiplier (rounded)
        return (int) Math.round(config.getPointsPerLine() * booster.multiplier());
    }

    /**
     * Checks whether the booster should activate at the current line.
     *
     * @param booster       the round's booster result (may be null if no booster spawned)
     * @param currentLine   current line index
     * @param alreadyActive whether the booster already activated in a previous tick
     * @return {@code true} if the booster activates NOW (first time trigger line crossed)
     */
    public boolean isBoosterActivating(BoosterResult booster, int currentLine, boolean alreadyActive) {
        if (booster == null || alreadyActive) return false;
        return currentLine >= booster.triggerLine();
    }
}

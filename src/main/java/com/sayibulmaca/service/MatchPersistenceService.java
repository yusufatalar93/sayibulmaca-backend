package com.sayibulmaca.service;

import com.sayibulmaca.dto.MatchDetailResponse;
import com.sayibulmaca.game.EndReason;
import com.sayibulmaca.game.LeagueUtil;
import com.sayibulmaca.game.MatchResult;
import com.sayibulmaca.game.MatchState;
import com.sayibulmaca.game.MatchStatus;
import com.sayibulmaca.game.TimeControl;
import com.sayibulmaca.model.Guess;
import com.sayibulmaca.model.Match;
import com.sayibulmaca.model.Rating;
import com.sayibulmaca.model.RatingId;
import com.sayibulmaca.model.User;
import com.sayibulmaca.repository.GuessRepository;
import com.sayibulmaca.repository.MatchRepository;
import com.sayibulmaca.repository.RatingRepository;
import com.sayibulmaca.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchPersistenceService {

    private final MatchRepository matchRepository;
    private final GuessRepository guessRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final EloService eloService;

    public record FinalizeResult(int ratingChangeA, int ratingChangeB, int newRatingA, int newRatingB,
                                  String newLeagueA, String newTierA, String newLeagueB, String newTierB) {}

    @Transactional
    public Match createMatch(UUID matchId, UUID playerAId, UUID playerBId, int digitMode, TimeControl timeControl) {
        User playerA = userRepository.getReferenceById(playerAId);
        User playerB = userRepository.getReferenceById(playerBId);
        Match match = Match.builder()
                .id(matchId)
                .digitMode((short) digitMode)
                .timeControl(timeControl)
                .playerA(playerA)
                .playerB(playerB)
                .status(MatchStatus.SETUP)
                .build();
        return matchRepository.save(match);
    }

    @Transactional
    public void updateMatchStarted(UUID matchId, String secretA, String secretB) {
        matchRepository.findById(matchId).ifPresent(match -> {
            match.setStatus(MatchStatus.IN_PROGRESS);
            match.setSecretA(secretA);
            match.setSecretB(secretB);
            match.setStartedAt(LocalDateTime.now());
            matchRepository.save(match);
        });
    }

    @Transactional
    public void saveGuess(UUID matchId, UUID playerId, int guessNumber, String value, int plus, int minus, int timeLeftMs) {
        Match matchRef = matchRepository.getReferenceById(matchId);
        User playerRef = userRepository.getReferenceById(playerId);
        Guess guess = Guess.builder()
                .match(matchRef)
                .player(playerRef)
                .guessNumber(guessNumber)
                .value(value)
                .plus((short) plus)
                .minus((short) minus)
                .timeLeftMs(timeLeftMs)
                .build();
        guessRepository.save(guess);
    }

    @Transactional
    public FinalizeResult finalizeMatch(UUID matchId, MatchState state) {
        MatchResult result = state.getResult();
        EndReason endReason = state.getEndReason();
        boolean isAbort = endReason == EndReason.ABORT;

        int changeA = 0, changeB = 0;
        int newRatingAVal = 1000, newRatingBVal = 1000;
        String newLeagueA = "SILVER", newTierA = "III", newLeagueB = "SILVER", newTierB = "III";

        if (!isAbort) {
            short digitMode = (short) state.getDigitMode();
            Rating ratingA = ratingRepository.findById(new RatingId(state.getPlayerAId(), digitMode)).orElse(null);
            Rating ratingB = ratingRepository.findById(new RatingId(state.getPlayerBId(), digitMode)).orElse(null);

            if (ratingA != null && ratingB != null) {
                double scoreA = result == MatchResult.A_WON ? 1.0 : (result == MatchResult.DRAW ? 0.5 : 0.0);
                double scoreB = result == MatchResult.B_WON ? 1.0 : (result == MatchResult.DRAW ? 0.5 : 0.0);
                changeA = eloService.calculateChange(ratingA.getRating(), ratingB.getRating(), scoreA, ratingA.getGamesPlayed());
                changeB = eloService.calculateChange(ratingB.getRating(), ratingA.getRating(), scoreB, ratingB.getGamesPlayed());

                ratingA.setRating(ratingA.getRating() + changeA);
                ratingB.setRating(ratingB.getRating() + changeB);
                ratingA.setGamesPlayed(ratingA.getGamesPlayed() + 1);
                ratingB.setGamesPlayed(ratingB.getGamesPlayed() + 1);

                if (result == MatchResult.A_WON) {
                    ratingA.setWins(ratingA.getWins() + 1);
                    ratingB.setLosses(ratingB.getLosses() + 1);
                } else if (result == MatchResult.B_WON) {
                    ratingA.setLosses(ratingA.getLosses() + 1);
                    ratingB.setWins(ratingB.getWins() + 1);
                } else {
                    ratingA.setDraws(ratingA.getDraws() + 1);
                    ratingB.setDraws(ratingB.getDraws() + 1);
                }
                ratingRepository.save(ratingA);
                ratingRepository.save(ratingB);

                newRatingAVal = ratingA.getRating();
                newRatingBVal = ratingB.getRating();
                newLeagueA = LeagueUtil.getLeague(newRatingAVal);
                newTierA = LeagueUtil.getTier(newRatingAVal);
                newLeagueB = LeagueUtil.getLeague(newRatingBVal);
                newTierB = LeagueUtil.getTier(newRatingBVal);
            }
        }

        final int finalChangeA = changeA;
        final int finalChangeB = changeB;
        matchRepository.findById(matchId).ifPresent(match -> {
            match.setStatus(isAbort ? MatchStatus.ABORTED : MatchStatus.FINISHED);
            match.setResult(result);
            match.setEndReason(endReason);
            match.setRatingChangeA(finalChangeA);
            match.setRatingChangeB(finalChangeB);
            match.setFinishedAt(LocalDateTime.now());
            matchRepository.save(match);
        });

        return new FinalizeResult(changeA, changeB, newRatingAVal, newRatingBVal,
                newLeagueA, newTierA, newLeagueB, newTierB);
    }

    public MatchDetailResponse getMatchDetail(UUID matchId, UUID requestingUserId) {
        Match match = matchRepository.findByIdWithPlayers(matchId).orElseThrow();
        List<Guess> guesses = guessRepository.findByMatchIdOrderByCreatedAt(matchId);

        List<MatchDetailResponse.GuessDto> guessDtos = guesses.stream()
                .map(g -> MatchDetailResponse.GuessDto.builder()
                        .playerId(g.getPlayer().getId())
                        .guessNumber(g.getGuessNumber())
                        .value(g.getValue())
                        .plus(g.getPlus())
                        .minus(g.getMinus())
                        .timeLeftMs(g.getTimeLeftMs())
                        .createdAt(g.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return MatchDetailResponse.builder()
                .matchId(match.getId())
                .playerAUsername(match.getPlayerA().getUsername())
                .playerBUsername(match.getPlayerB().getUsername())
                .digitMode(match.getDigitMode())
                .timeControl(match.getTimeControl().name())
                .secretA(match.getSecretA())
                .secretB(match.getSecretB())
                .status(match.getStatus().name())
                .result(match.getResult() != null ? match.getResult().name() : null)
                .endReason(match.getEndReason() != null ? match.getEndReason().name() : null)
                .ratingChangeA(match.getRatingChangeA())
                .ratingChangeB(match.getRatingChangeB())
                .startedAt(match.getStartedAt())
                .finishedAt(match.getFinishedAt())
                .guesses(guessDtos)
                .build();
    }
}

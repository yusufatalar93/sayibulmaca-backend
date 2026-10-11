CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(20) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ratings (
    user_id UUID NOT NULL REFERENCES users(id),
    digit_mode SMALLINT NOT NULL CHECK (digit_mode IN (4, 5)),
    rating INT NOT NULL DEFAULT 1000,
    games_played INT NOT NULL DEFAULT 0,
    wins INT NOT NULL DEFAULT 0,
    losses INT NOT NULL DEFAULT 0,
    draws INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, digit_mode)
);

CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    digit_mode SMALLINT NOT NULL CHECK (digit_mode IN (4, 5)),
    time_control VARCHAR(10) NOT NULL CHECK (time_control IN ('FAST', 'NORMAL', 'LONG')),
    player_a_id UUID NOT NULL REFERENCES users(id),
    player_b_id UUID NOT NULL REFERENCES users(id),
    secret_a VARCHAR(5),
    secret_b VARCHAR(5),
    status VARCHAR(20) NOT NULL DEFAULT 'SETUP' CHECK (status IN ('SETUP', 'IN_PROGRESS', 'FINISHED', 'ABORTED')),
    result VARCHAR(10) CHECK (result IN ('A_WON', 'B_WON', 'DRAW')),
    end_reason VARCHAR(15) CHECK (end_reason IN ('SOLVED', 'TIMEOUT', 'RESIGN', 'ABORT', 'GUESS_LIMIT')),
    rating_change_a INT,
    rating_change_b INT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE TABLE guesses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id UUID NOT NULL REFERENCES matches(id),
    player_id UUID NOT NULL REFERENCES users(id),
    guess_number INT NOT NULL,
    value VARCHAR(5) NOT NULL,
    plus SMALLINT NOT NULL,
    minus SMALLINT NOT NULL,
    time_left_ms INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_matches_player_a ON matches(player_a_id);
CREATE INDEX idx_matches_player_b ON matches(player_b_id);
CREATE INDEX idx_guesses_match ON guesses(match_id);

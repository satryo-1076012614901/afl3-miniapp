CREATE TABLE match (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    competition_id   BIGINT      NOT NULL REFERENCES competition (id),
    participant_1_id BIGINT      REFERENCES participant (id),
    participant_2_id BIGINT      REFERENCES participant (id),
    winner_id        BIGINT      REFERENCES participant (id),
    next_match_id    BIGINT      REFERENCES match (id),
    round            INTEGER     NOT NULL,
    match_number     INTEGER     NOT NULL,
    score_1          INTEGER,
    score_2          INTEGER,
    status           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT match_position_unique UNIQUE (competition_id, round, match_number),
    CONSTRAINT match_round_positive CHECK (round >= 1),
    CONSTRAINT match_number_positive CHECK (match_number >= 1),
    CONSTRAINT match_score_1_non_negative CHECK (score_1 IS NULL OR score_1 >= 0),
    CONSTRAINT match_score_2_non_negative CHECK (score_2 IS NULL OR score_2 >= 0),
    CONSTRAINT match_participants_distinct
        CHECK (participant_1_id IS NULL OR participant_2_id IS NULL OR participant_1_id <> participant_2_id),
    CONSTRAINT match_status_check CHECK (status IN ('PENDING', 'READY', 'COMPLETED'))
);

CREATE INDEX match_competition_order_idx
    ON match (competition_id, round, match_number);

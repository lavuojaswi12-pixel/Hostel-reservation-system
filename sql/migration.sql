-- Upgrade an EXISTING hotel_db created from the original README (no check_in / check_out).
-- Back up first:  mysqldump hotel_db > backup.sql
USE hotel_db;

ALTER TABLE reservations
    ADD COLUMN check_in  DATE NULL AFTER phone,
    ADD COLUMN check_out DATE NULL AFTER check_in;

-- Old rows had no stay dates: assume a one-night stay starting on the booking date.
UPDATE reservations
SET check_in  = `date`,
    check_out = DATE_ADD(`date`, INTERVAL 1 DAY)
WHERE check_in IS NULL;

-- The old app stored the time as HH-mm; the new one uses HH:mm.
UPDATE reservations SET `time` = REPLACE(`time`, '-', ':');

ALTER TABLE reservations
    MODIFY check_in  DATE NOT NULL,
    MODIFY check_out DATE NOT NULL,
    ADD CONSTRAINT chk_stay CHECK (check_out > check_in),
    ADD INDEX idx_room_stay (room, check_in, check_out),
    ADD INDEX idx_phone (phone);

-- NOTE: the old app allowed the same room to be booked many times. If you have such rows,
-- find the overlaps and fix them by hand:
-- SELECT a.id, b.id, a.room FROM reservations a JOIN reservations b
--   ON a.room = b.room AND a.id < b.id AND a.check_in < b.check_out AND a.check_out > b.check_in;

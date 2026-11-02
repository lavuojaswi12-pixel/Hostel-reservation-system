-- Fresh install
CREATE DATABASE IF NOT EXISTS hotel_db;
USE hotel_db;

CREATE TABLE IF NOT EXISTS reservations (
    id        INT PRIMARY KEY AUTO_INCREMENT,
    name      VARCHAR(100) NOT NULL,
    room      INT          NOT NULL,
    email     VARCHAR(150) NOT NULL,
    phone     VARCHAR(30)  NOT NULL,
    check_in  DATE         NOT NULL,
    check_out DATE         NOT NULL,
    `date`    DATE         NOT NULL,   -- day the booking was made
    `time`    VARCHAR(10)  NOT NULL,   -- time the booking was made (HH:mm)
    CONSTRAINT chk_stay CHECK (check_out > check_in),   -- enforced on MySQL 8.0.16+
    INDEX idx_room_stay (room, check_in, check_out),
    INDEX idx_phone (phone)
);

package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Reservation {
    private int id;
    private String clientName;
    private String clientPhone;
    private String clientEmail;
    private int roomNo;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private LocalDate reservationDate;   // when the booking was made
    private String reservationTime;      // HH:mm

    public int getId() {return id;}
    public String getClientName() {return clientName;}
    public String getClientPhone() {return clientPhone;}
    public String getClientEmail() {return clientEmail;}
    public int getRoomNo() {return roomNo;}
    public LocalDate getCheckIn() {return checkIn;}
    public LocalDate getCheckOut() {return checkOut;}
    public LocalDate getReservationDate() {return reservationDate;}
    public String getReservationTime() {return reservationTime;}

    public void setId(int id) {this.id = id;}
    public void setClientName(String clientName) {this.clientName = clientName;}
    public void setClientPhone(String clientPhone) {this.clientPhone = clientPhone;}
    public void setClientEmail(String clientEmail) {this.clientEmail = clientEmail;}
    public void setRoomNo(int roomNo) {this.roomNo = roomNo;}
    public void setCheckIn(LocalDate checkIn) {this.checkIn = checkIn;}
    public void setCheckOut(LocalDate checkOut) {this.checkOut = checkOut;}
    public void setReservationDate(LocalDate reservationDate) {this.reservationDate = reservationDate;}
    public void setReservationTime(String reservationTime) {this.reservationTime = reservationTime;}

    public long getNights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /**
     * True if this stay overlaps the half-open range [otherIn, otherOut).
     * A guest checking out on the day another checks in is NOT an overlap.
     */
    public boolean overlaps(LocalDate otherIn, LocalDate otherOut) {
        return checkIn.isBefore(otherOut) && checkOut.isAfter(otherIn);
    }

    public Reservation copy() {
        Reservation c = new Reservation();
        c.id = id;
        c.clientName = clientName;
        c.clientPhone = clientPhone;
        c.clientEmail = clientEmail;
        c.roomNo = roomNo;
        c.checkIn = checkIn;
        c.checkOut = checkOut;
        c.reservationDate = reservationDate;
        c.reservationTime = reservationTime;
        return c;
    }
}

package com.barriodigital.ms_requests.messaging;

public record CrewTicketEvent(String ticketId, String category, String location, String priority) { }
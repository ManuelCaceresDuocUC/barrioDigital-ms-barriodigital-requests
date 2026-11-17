package com.barriodigital.ms_requests.messaging;

/**
 * QUÉ ES: contenido del comando "ticket de visita a cuadrilla" (lo consume notify).
 * Se envía cuando la solicitud pasa a EN_TERRENO.
 */
public record CrewTicketEvent(String ticketId, String category, String location, String priority) { }

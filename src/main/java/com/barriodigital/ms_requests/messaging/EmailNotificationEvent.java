package com.barriodigital.ms_requests.messaging;

/**
 * QUÉ ES: contenido del comando "enviar correo" (lo consume notify). Se arma en RequestMessages.email.
 * Los campos coinciden con el record del mismo nombre en notify: es el "contrato" entre ambos servicios.
 */
public record EmailNotificationEvent(String to, String subject, String body, String procedureId) { }

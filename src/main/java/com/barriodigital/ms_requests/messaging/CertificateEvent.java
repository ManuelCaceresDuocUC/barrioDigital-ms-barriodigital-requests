package com.barriodigital.ms_requests.messaging;

/**
 * QUÉ ES: contenido del comando "generar certificado" que se pide publicar (lo consume notify).
 * Solo se envía cuando la solicitud llega a RESUELTO y trae RUT.
 */
public record CertificateEvent(String citizenRut, String certificateType, String documentUrl) { }

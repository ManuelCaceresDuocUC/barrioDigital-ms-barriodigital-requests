package com.barriodigital.ms_requests.messaging;

public record CertificateEvent(String citizenRut, String certificateType, String documentUrl) { }
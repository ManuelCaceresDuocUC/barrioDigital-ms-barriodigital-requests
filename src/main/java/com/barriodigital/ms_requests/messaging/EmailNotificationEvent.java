package com.barriodigital.ms_requests.messaging;

public record EmailNotificationEvent(String to, String subject, String body, String procedureId) { }
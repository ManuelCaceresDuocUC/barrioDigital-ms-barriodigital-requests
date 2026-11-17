package com.barriodigital.ms_requests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * QUÉ ES: punto de entrada del microservicio de solicitudes (ms-requests), el corazón del negocio.
 *
 * QUÉ HACE: gestiona las solicitudes de los vecinos (crear, consultar, cambiar de estado con una máquina de
 * estados, historial). Habla con: Oracle (guarda), catalog (valida trámite y descuenta cupo), rabbitadmin
 * (pide publicar comandos: correo, ticket) y Kafka (publica hechos para report y audit).
 */
@SpringBootApplication
public class MsRequestsApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsRequestsApplication.class, args);
	}

}

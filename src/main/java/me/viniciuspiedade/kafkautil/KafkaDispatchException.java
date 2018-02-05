package me.viniciuspiedade.kafkautil;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;

public class KafkaDispatchException extends RuntimeException {

	private static final long serialVersionUID = 5101221435327075201L;
	
	public KafkaDispatchException(String message, IOException e) {
		super(message, e);
	}

	public KafkaDispatchException(JsonProcessingException e) {
		super(e);
	}
}

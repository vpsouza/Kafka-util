package me.viniciuspiedade.kafkautil;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractKafkaDispatcher<T> {

	private final Class<T> payloadClass;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final String topicName;
	private final ExecutorService executor;

	public AbstractKafkaDispatcher(Class<T> payloadClass, KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper objectMapper, String topicName) {
		this.payloadClass = payloadClass;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.topicName = topicName;
		this.executor = Executors.newSingleThreadExecutor();
	}

	public AbstractKafkaDispatcher(ExecutorService executor, Class<T> payloadClass,
			KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, String topicName) {
		this.payloadClass = payloadClass;
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
		this.topicName = topicName;
		this.executor = executor;
	}

	protected abstract Function<T, T> beforeDispatch();

	protected abstract Function<T, T> afterDispatch();

	public void dispatch(final T payload) {
		CompletableFuture.supplyAsync(() -> doDispatch(payload, beforeDispatch()), executor)
				.thenAccept(result -> doAfterDispatch(result, afterDispatch()));
	}

	private CompletableFuture<SendResult<String, String>> doDispatch(T payload, Function<T, T> beforeDispatch) {
		try {
			return kafkaTemplate.send(topicName, objectMapper.writeValueAsString(beforeDispatch.apply(payload)))
					.completable();
		} catch (JsonProcessingException e1) {
			throw new KafkaDispatchException(e1);
		}
	}

	private void doAfterDispatch(CompletableFuture<SendResult<String, String>> result, Function<T, T> afterDispatch) {
		result.whenComplete((theResult, error) -> Optional.ofNullable(theResult).ifPresent(elm -> {
			String producedRecord = elm.getProducerRecord().value();
			try {
				afterDispatch.apply(objectMapper.readValue(producedRecord, payloadClass));
			} catch (IOException e) {
				throw new KafkaDispatchException("Content of dispatcher unknown: \\n\\n".concat(producedRecord), e);
			}
		}));
	}
}

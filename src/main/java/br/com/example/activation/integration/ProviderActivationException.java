package br.com.example.activation.integration;
public class ProviderActivationException extends RuntimeException {
 private final boolean retryable;
 public ProviderActivationException(String message,boolean retryable){super(message);this.retryable=retryable;}
 public boolean isRetryable(){return retryable;}
}

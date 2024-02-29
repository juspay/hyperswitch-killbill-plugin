package org.killbill.billing.plugin.hyperswitch.exception;


import java.io.IOException;

public class FormaterException extends RuntimeException {

  public FormaterException(IOException e) {
    super(e);
  }
}

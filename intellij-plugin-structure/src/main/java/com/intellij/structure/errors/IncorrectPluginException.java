package com.intellij.structure.errors;

/**
 * @author Sergey Patrikeev
 */
public class IncorrectPluginException extends RuntimeException {

  public IncorrectPluginException(String message) {
    super(message);
  }

  public IncorrectPluginException(String message, Throwable cause) {
    super(message, cause);
  }

}

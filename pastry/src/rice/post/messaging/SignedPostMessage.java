package rice.post.messaging;

import java.io.*;

import rice.post.*;
import rice.post.security.*;

/**
 * This class is the representation of a PostMessage and
 * it's attached signature.  This class should be the
 * one which is sent across the wire.
 */
public final class SignedPostMessage implements Serializable {

  // the PostMessage
  private PostMessage message;

  // the signature for this message
  private PostSignature signature;

  /**
   * Constructs a SignedPostMessage given the message and
   * siganture
   *
   * @param sender The sender of this message.
   */
  public SignedPostMessage(PostMessage message, PostSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  /**
   * Returns the sender of this message.
   *
   * @return The sender
   */
  public PostMessage getMessage() {
    return message;
  }

  /**
    * Returns the signature for this message, or null
   * if the message has not yet been signed.
   *
   * @return The signature, or null if not yet signed.
   */
  public PostSignature getSignature() {
    return signature;
  }
}

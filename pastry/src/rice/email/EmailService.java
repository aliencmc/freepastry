package rice.email;

import java.security.*;
import java.util.*;

import rice.*;
import rice.p2p.past.*;
import rice.Continuation.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.email.proxy.smtp.manager.*;
import rice.environment.logging.Logger;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;

/**
 * This class serves as the entry point into the email service written on top of
 * Post.
 * 
 * The EmailService uses the observer pattern to notify other objects of newly
 * received emails. The event generated will contain an {@link Email} object as
 * its argument.
 *
 * @version $Id: pretty.settings,v 1.1 2003/07/10 02:18:11 amislove Exp $
 * @author Alan Mislove
 */
public class EmailService extends PostClient {

  // the Emails Service's Post object
  private Post post;

  // the root folder
  private Folder folder;

  // the inbox folder
  private Folder inbox;

  // the keypair used to encrypt the log
  private KeyPair keyPair;
  
  // whether to allow reinsertion of log head
  private boolean logRewrite;
  
  // a temporary list of recently received emails
  private HashSet received;

  /**
   * the name of the Inbox's log
   */
  public final static String INBOX_NAME = "INBOX";

  /**
   * Constructor
   *
   * @param post The Post service to use
   * @param keyPair The keyPair of the local user
   */
  public EmailService(Post post, KeyPair keyPair, boolean logRewrite) {
    this.post = post;
    this.keyPair = keyPair;
    this.logRewrite = logRewrite;
    this.received = new HashSet();

    post.addClient(this);
  }

  /**
   * @return the post object this serivce is using.
   */
  public Post getPost() {
    return post;
  }
  
  /**
   * Reset the inbox folder to be a different folder.  This should be done only
   * if you know what you're doing.
   *
   * @param folder The new inbox
   */
  public void setInbox(Folder folder) {
    this.inbox = folder;
  } 

  /**
   * Returns the Log for ePost's root folder.
   *
   * @param command is the object notified of the result of the folder
   *      retrieval.
   */
  public void getRootFolder(final Continuation command) {
    if (folder != null) {
      command.receiveResult(folder);
      return;
    }

    post.getPostLog(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        final PostLog mainLog = (PostLog) o;

        if (mainLog == null) {
          command.receiveException(new Exception("PostLog was null - aborting."));
        } else {
          mainLog.getChildLog(getAddress(), new StandardContinuation(parent) {
            public void receiveException(Exception e) {
              if (e instanceof StorageException)
                receiveResult(null);
              else
                parent.receiveException(e);
            }
            
            public void receiveResult(Object o) {
              EmailLog log = (EmailLog) o;

              if (log == null) {
                if (logRewrite) {
                  EmailLog emailRootLog = new EmailLog(getAddress(), post.getStorageService().getRandomNodeId(), post, keyPair);
                  mainLog.addChildLog(emailRootLog, new StandardContinuation(parent) {
                    public void receiveResult(Object o) {
                      folder = new Folder((EmailLog) o, post, keyPair);
                      
                      folder.createChildFolder(INBOX_NAME, new StandardContinuation(parent) {
                        public void receiveResult(Object o) {
                          inbox = (Folder) o;
                          
                          if (inbox == null) {
                            command.receiveException(new Exception("Could not create INBOX folder - got null"));
                          } else {
                            command.receiveResult(folder);
                          }
                        }
                      });
                    }
                  });
                } else {
                  command.receiveException(new Exception("Could not find email root log - not allowed to insert.  Aborting..."));
                }
              } else {
                folder = new Folder(log, post, keyPair);
                folder.getChildFolder(INBOX_NAME, new StandardContinuation(parent) {
                  public void receiveResult(Object o) {
                    inbox = (Folder) o;
                    
                    if (inbox == null) {
                      if (logRewrite) {
                        folder.createChildFolder(INBOX_NAME, new StandardContinuation(parent) {
                          public void receiveResult(Object o) {
                            inbox = (Folder) o;
                            
                            if (inbox == null) {
                              command.receiveException(new Exception("Could not create INBOX folder - got null"));
                            } else {
                              command.receiveResult(folder);
                            }
                          }
                        });
                      } else {
                        command.receiveException(new Exception("Could not fetch INBOX folder - got null and not allowed to insert"));
                      }
                    } else {
                      command.receiveResult(folder);
                    }
                  }
                });
              }
            }
          });
        }
      }
    });
  }
  
  /**
   * Method which expands the given list of PostUserAddresses by recursively checking
   * for forwarding lists
   *
   * @param list The list
   * @param command The command to return the result to (a String[])
   */
  public void expand(PostUserAddress[] list, final SmtpManager manager, Continuation command) {
    final HashSet expanded = new HashSet();
    final HashSet toExpand = new HashSet();
    
    for (int i=0; i<list.length; i++) 
      toExpand.add(list[i]);
    
    final Continuation c = new StandardContinuation(command) {
      public void next() {
        receiveResult(null);
      }
      
      public void receiveResult(Object o) {
        Iterator i = toExpand.iterator();
        
        if (i.hasNext()) {
          final PostUserAddress address = (PostUserAddress) i.next();
          toExpand.remove(address);
          expanded.add(address);
          
          // and finally check for any forwarding addresses
          post.getPostLog(address, new StandardContinuation(parent) {
            public void receiveResult(Object o) {    
              if (o != null) {
                ((Log) o).getChildLog(ForwardLog.FORWARD_NAME, new StandardContinuation(parent) {
                  public void receiveResult(Object o) {
                    if (o != null) {
                      String[] addresses = ((ForwardLog) o).getAddresses();
                      
                      if (addresses != null) {
                        for (int j=0; j<addresses.length; j++) {
                          if (manager.isPostAddress(addresses[j])) {
                            PostUserAddress pua = new PostUserAddress(rice.email.proxy.mailbox.postbox.PostMessage.factory, addresses[j], post.getEnvironment());
                            if (! expanded.contains(pua))
                              toExpand.add(pua);
                          } else {
                            expanded.add(addresses[j]);
                          }
                        } 
                      }
                    }
                    
                    next();
                  }
                });
              } else {
                System.err.println("WARNING: Could not find PostLog for forwarding for " + address);
                next();
              }
            }
          });
        } else {       
          // pass any result from the Store Data (there should be none) to the handler.
          parent.receiveResult(expanded.toArray());
        }
      }
    };
    
    c.receiveResult(null);
  }

  /**
   * Sends the email to the recipient. The Email object has a notion of who its
   * recipients are.
   *
   * @param email The email to send
   * @param command is the object that will be notified of errors that occur
   *      during the send procedure, or Boolean(true) if it succeeds.
   * @exception PostException DESCRIBE THE EXCEPTION
   */
  public void sendMessage(final Email email, final Continuation command) throws PostException {
    // get the storage service, and let the Email itself know about the Service
    email.setStorage(post.getStorageService());

    // start storing the data
    email.storeData(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        if (o.equals(new Boolean(true))) {
          new StandardContinuation(parent) {
            protected int i=0;
            
            public void receiveResult(Object o) {
              if (i < email.getRecipients().length) 
                post.sendNotification(new EmailNotificationMessage(email, email.getRecipients()[i++], EmailService.this), this);
              else
                parent.receiveResult(Boolean.TRUE);
            }
          }.receiveResult(null);
        } else {
          command.receiveException(new Exception("Storing of Email did not succeed: " + o));
        }
      }
    });
  }

  /**
   * This method is how the Post layer informs the EmailService layer that there
   * is an incoming notification of new email.
   *
   * @param nm The incoming notification.
   */
  public void notificationReceived(NotificationMessage nm, Continuation command) {
    if (nm instanceof EmailNotificationMessage) {
      final EmailNotificationMessage enm = (EmailNotificationMessage) nm;
      
      // in case we've already received it, just say ok
      if (received.contains(enm.getEmail())) {
        post.getEnvironment().getLogManager().getLogger(EmailService.class, null).log(Logger.WARNING,
            "Received duplicate email from " + enm.getEmail().getSender() + " - silently accepting");
        command.receiveResult(Boolean.TRUE);
        return;
      }
      
      try {
        enm.getEmail().setStorage(post.getStorageService());
      } catch (NullPointerException e) {
        command.receiveResult(Boolean.FALSE);
        return;
      }

      post.getEnvironment().getLogManager().getLogger(EmailService.class, null).log(Logger.FINE,
          "Received email from " + enm.getEmail().getSender());

      // notify the observers that an email has been received.
      this.setChanged();
      this.notifyObservers(enm);

      if (inbox != null) {
        inbox.addMessage(enm.getEmail(), new StandardContinuation(command) {
          public void receiveResult(Object o) {
            HashSet set = new HashSet();
            enm.getEmail().getContentHashReferences(set);
            ContentHashReference[] references = (ContentHashReference[]) set.toArray(new ContentHashReference[0]);
            received.add(enm.getEmail());
            
            post.getStorageService().refreshContentHash(references, new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                parent.receiveResult(Boolean.TRUE);
              }
            });
          }
        });
      } else {
        post.getEnvironment().getLogManager().getLogger(EmailService.class, null).log(Logger.WARNING,
            "Recieved message, but was unable to insert due to null inbox...");
        command.receiveResult(Boolean.FALSE);
      }
    } else {
      post.getEnvironment().getLogManager().getLogger(EmailService.class, null).log(Logger.WARNING,
          "EmailService received unknown notification " + nm + " - dropping on floor.");
      command.receiveException(new PostException("EmailService received unknown notification " + nm + " - dropping on floor."));
    }
  }
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all handles under which the application has live objects.  This used to
   * implement the garbage collection service, thus, the application must
   * ensure that all data which it is still interested in is returned.
   *
   * The applications should return a ContentHashReference[] containing all of 
   * the handles The application is still interested in to the provided continatuion.
   */
  public void getContentHashReferences(Continuation command) {
    final Set set = new HashSet();

    if (folder != null) {
      folder.getContentHashReferences(set, new StandardContinuation(command) {
        public void receiveResult(Object o) {
          parent.receiveResult(set.toArray(new ContentHashReference[0]));
        }
      });
    } else {
      command.receiveResult(new ContentHashReference[0]);
    }
  }
  
  /**
    * This method is periodically invoked by Post in order to get a list of
   * all mutable data which the application is interested in.
   *
   * The applications should return a Log[] containing all of 
   * the data The application is still interested in to the provided continatuion.
   */
  public void getLogs(Continuation command) {
    final Set set = new HashSet();
    folder.getLogs(set, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        parent.receiveResult(set.toArray(new Log[0]));
      }
    });
  }
  
  /**
   * Returns the list of subscriptions in the log
   *
   * @return The subscriptions
   */
  public void getSubscriptions(Continuation command) {
    folder.getSubscriptions(command);
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param sub The subscription to add
   */
  public void addSubscription(String sub, Continuation command) {
    folder.addSubscription(sub, command);
  }
  
  /**
   * Adds a subscriptions to the log
   *
   * @param sub The subscription to add
   */
  public void removeSubscription(String sub, Continuation command) {
    folder.removeSubscription(sub, command);
  }
}


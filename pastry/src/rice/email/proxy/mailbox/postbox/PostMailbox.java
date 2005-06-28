package rice.email.proxy.mailbox.postbox;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.imap.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.util.*;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.*;

import rice.*;
import rice.Continuation.*;

import rice.email.EmailService;
import rice.email.Folder;
import rice.environment.Environment;
import rice.environment.logging.Logger;

/**
 * This class serves as the main "glue" code between foedus and
 * the POST-based email implementation.
 */
public class PostMailbox implements Mailbox {

  // the local email service to use
  protected EmailService email;
  
  // the root folder for this user
  protected PostFolder root;

  // the hierarchy delimiter used by ePOST
  public static String HIERARCHY_DELIMITER = "/";

  /**
   * Constructs a PostMailbox given an emailservice
   * to run off of.
   *
   * @param email The email service on the local pastry node.
   */
  public PostMailbox(EmailService email, Folder root, Environment env) throws MailboxException {
    if (email == null)
      throw new IllegalArgumentException("EmailService cannot be null in PostMailbox.");

    this.email = email;
    
    if (root != null)
      this.root = new PostFolder(root, null, email);
    else
      env.getLogManager().getLogger(MovingMessage.class, null).log(Logger.INFO, 
        "Root folder is null - folder operations will likely cause a NPE");
  }
  
  /**
   * Returns the hierarchy delimiter used by this mailbox
   *
   * @return The hierarchy delimiter
   */
  public String getHierarchyDelimiter() {
    return HIERARCHY_DELIMITER;
  }
  
  /**
   * Returns the root folder of the user's mailbox. Note that
   * this method blocks while fetching the folder.
   *
   * @throws MailboxException If an error occurs.
   * @return The root folder.
   */
  public MailFolder getRootFolder() throws MailboxException {
    return root;
  }

  /**
   * Adds the given message to this folder
   *
   * @param msg The message to add
   */
  public void put(MovingMessage msg) throws MailboxException {
    getFolder(EmailService.INBOX_NAME).put(msg);
  }
  
  /**
   * Creates a folder with the given name.  If the name contains 
   * the heirarchy character, it is interpreted as a hierchy and any
   * necessary folders are created, too.
   *
   * @param folder The name to create
   */
  public void createFolder(String name) throws MailboxException {
    if (name.trim().toLowerCase().equals("inbox")) 
      throw new MailboxException("Cannot create folder with name '" + name + "'.");
    
    String[] names = name.split(HIERARCHY_DELIMITER);
    PostFolder folder = (PostFolder) getRootFolder();
    
    for (int i=0; i<names.length; i++) {
      try {
        folder = (PostFolder) folder.getChild(names[i]);
      } catch (MailboxException e) {
        folder = (PostFolder) folder.createChild(names[i]);
      }
    }
  }
  
  /**
   * Fetches a given folder name. This name can represent a hierarhy, if it
   * contains the hierarhy delimiting character.
   *
   * @param name The name of the folder
   * @return The specificed MailFolder.
   */
  public MailFolder getFolder(String name) throws MailboxException {
    if (name.trim().toLowerCase().equals("inbox"))
      name = EmailService.INBOX_NAME;
    
    String[] names = name.split(HIERARCHY_DELIMITER);
    PostFolder folder = (PostFolder) getRootFolder();
    
    for (int i=0; i<names.length; i++) 
      folder = (PostFolder) folder.getChild(names[i]);
    
    return folder;
  }
  
  /**
   * Lists all folders
   *
   * @return All available folders
   */
  protected MailFolder[] listFolders() throws MailboxException {
    Vector result = new Vector();
    walk((PostFolder) getRootFolder(), result);
    
    return (MailFolder[]) result.toArray(new MailFolder[0]);
  }
  
  /**
   * Lists all folders which match the provided pattern
   *
   * @param pattern The pattern to match against
   * @return The folders which match
   */
  public MailFolder[] listFolders(String pattern) throws MailboxException {
    MailFolder[] folders = listFolders();
    Vector result = new Vector();
    
    pattern = pattern.replaceAll("\\*", ".*").replaceAll("\\%", "[^" + HIERARCHY_DELIMITER + "]*");
    
    for (int i = 0; i < folders.length; i++) 
      if (folders[i].getFullName().matches(pattern) || folders[i].getFullName().equals(pattern))
        result.add(folders[i]);
    
    return (MailFolder[]) result.toArray(new MailFolder[0]);
  }  

  /**
   * Renames the given folder to the new name.  If the new name represents a hierarchy,
   * any necessary folders are automatically created.
   *
   * @param old_name The current name
   * @param new_name The new name
   */
  public void renameFolder(String old_name, String new_name) throws MailboxException {
    boolean exists = true;
    
    try {
      getFolder(new_name);      
    } catch (MailboxException e) {
      exists = false;
    }
    
    if (exists)
      throw new MailboxException("Folder " + new_name + " already exists!"); 
    
    PostFolder old = (PostFolder) getFolder(old_name); 
    old.delete(false);
    
    String[] names = new_name.split(HIERARCHY_DELIMITER);

    ExternalContinuation c = new ExternalContinuation();
    old.getFolder().setName(names[names.length-1], c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); } 
    
    PostFolder parent = (PostFolder) getRootFolder();
    
    for (int i=0; i<names.length-1; i++) {
      try {
        parent = (PostFolder) parent.getChild(names[i]);
      } catch (MailboxException e) {
        parent = (PostFolder) parent.createChild(names[i]);
      }
    }
    
    ExternalContinuation d = new ExternalContinuation();
    parent.getFolder().addChildFolder(old.getFolder(), d);
    d.sleep();
    
    if (d.exceptionThrown()) { throw new MailboxException(d.getException()); } 
    
    old.setParent(parent);
    
    if (old_name.trim().toLowerCase().equals("inbox")) {
      root.createChild(EmailService.INBOX_NAME);
      email.setInbox(((PostFolder) getFolder(EmailService.INBOX_NAME)).getFolder()); 
    }
  }

  /**
   * Deletes the folder associated with the given name.  If the folder
   * has child folders, a MailboxException is thrown.
   *
   * @param name The name of the folder to delete
   */
  public void deleteFolder(String name) throws MailboxException {
    if (name.trim().toLowerCase().equals(EmailService.INBOX_NAME))
      throw new MailboxException("INBOX folder cannot be deleted.");
    
    ((PostFolder) getFolder(name)).delete();
  }
  
  protected void walk(PostFolder folder, Vector result) throws MailboxException {
    MailFolder[] folders = folder.getChildren();
    
    for (int i=0; i<folders.length; i++) {
      result.add(folders[i]);
      walk((PostFolder) folders[i], result);
    }
  }

  public void subscribe(String fullName) throws MailboxException {
    if (listSubscriptions(fullName).length != 0)
	  return;
	  
    try {
      getFolder(fullName);
    } catch (MailboxException e) {
      return;
    }
	
    ExternalContinuation c = new ExternalContinuation();
    email.addSubscription(fullName, c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); } 
  }

  public void unsubscribe(String fullName) throws MailboxException {
    if (listSubscriptions(fullName).length == 0)
	  return;
	  
    ExternalContinuation c = new ExternalContinuation();
    email.removeSubscription(fullName, c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); } 
  }
  
  public String[] listSubscriptions(String pattern) throws MailboxException {
    ExternalContinuation c = new ExternalContinuation();
    email.getSubscriptions(c);
    c.sleep();
    
    if (c.exceptionThrown()) { throw new MailboxException(c.getException()); } 
    
    Vector result = new Vector();
    String[] subscriptions = (String[]) c.getResult();
    
    pattern = pattern.replaceAll("\\*", ".*").replaceAll("\\%", "[^" + HIERARCHY_DELIMITER + "]*");
    
    for (int i=subscriptions.length-1; i>=0; i--) {
      String str = subscriptions[i];
      
      /* FIX FOR FOLDERS - DON'T Touch ABP4 */
      if (str.matches(pattern) || str.equals(pattern)) {
        try {
          if (getFolder(str) != null) {
            result.add(str);
          }
        } catch (MailboxException me) {
          /* do nothing */
        }
      }
    }
    
    return (String[]) result.toArray(new String[0]);
  }
}





/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
Basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.persistence; 

/*
 * @(#) PersistenceManager.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 *
 * @version $Id$
 */
import java.io.*;
import java.util.*;

import rice.*;
import rice.pastry.*;

/**
 * This class is an implementation of Storage which provides
 * persistent storage to disk.  This class also guarentees that
 * the data will be consistent, even after a crash.  This class
 * also provides these services is a non-blocking fashion by
 * launching a seperate thread which is tasked with actaully
 * writing the data to disk.
 */
public class PersistentStorage implements Storage {
 
  private String name;
  private File rootDirectory;       // root directory to store stuff in
  private File backupDirectory;     // dir for storing persistent objs
  private File appDirectory;        // dir for storing persistent objs
  private File lostDirectory;       // dir for lost objects

  private static String rootDir;                          // rootDirectory
  private static final String backupDir = "/FreePastry-Storage-Root/"; // backupDirectory

  private long storageSize; /* The amount of storage allowed to be used */
  private long usedSize; /* The amount of storage currently in use */
  private IdSet idSet;
  
  private final int MAX_FILES = 1000; /* The maximum number of files in a dir */
  private final boolean DEBUG = true;
  /**
   * Builds a PersistentStorage given a root directory in which to
   * persist the data.
   *
   * @param rootDir The root directory of the persisted disk.
   */
  public PersistentStorage(String rootDir, int size) {
    this("default", rootDir, size);
  }
 
  public PersistentStorage(String name, String rootDir, int size){
    this.name = name;
    this.rootDir = rootDir;
    storageSize = size; 

    init();
  }

  /**
   * Makes the object persistent to disk and stored permanantly
   *
   * If the object is already persistent, this method will
   * simply update the object's serialized image.
   *
   * This is implemented atomically so that this may succeed
   * and store the new object, or fail and leave the previous
   * object intact.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param obj The object to be made persistent.
   * @param id The object's id.
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void store(Id id, Serializable obj, Continuation c) {

    try {
      if (id == null || obj == null) {
        c.receiveResult(new Boolean(false));
        return;
      }

      /* Create a file representation and then transactionally write it */
      File objFile = getFile(id); 
      /* change the name here */
      File transcFile = makeFile((Id) id);
      writeObject(obj, id, readVersion(objFile) + 1, transcFile);


      if( getUsedSpace() + getFileLength(objFile) > getStorageSize()){
         /* abort, this will put us over quota */
         deleteFile(transcFile); 
         c.receiveResult(new Boolean(false));
         return;
      }
      else{
        /* complete transaction */
        decreaseUsedSpace(getFileLength(objFile)); /* decrease amount used */
        deleteFile(objFile);
        increaseUsedSpace(transcFile.length()); /* increase the amount used */
        idSet.addMember(id); 
        if(numFilesDir(transcFile.getParentFile()) > MAX_FILES){
            expandDirectory(transcFile.getParentFile());
        }
      } 

      c.receiveResult(new Boolean(true));

    } catch (Exception e) {
       e.printStackTrace();
       c.receiveException(e);
    }
  }

  /**
   * Request to remove the object from the list of persistend objects.
   * Delete the serialized image of the object from stable storage. If
   * necessary. If the object was not in the cached list in the first place,
   * nothing happens and <code>false</code> is returned.
   *
   * This method also guarantees that the data on disk will remain consistent,
   * even after a crash by performing the delete atomically.
   *
   * This method completes by calling recieveResult() of the provided continuation
   * with the success or failure of the operation.
   *
   * @param id The object's persistence id
   * @param c The command to run once the operation is complete
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public void unstore(Id id, Continuation c) {

      File objFile = getFile(id); 
      if(objFile == null){
       c.receiveResult(new Boolean(false));
       return;
      } 
      idSet.removeMember((Id) id);
      decreaseUsedSpace(objFile.length());
      objFile.delete();
       
      c.receiveResult(new Boolean(true));
  }

  /**
   * Returns whether or not an object is present in the location <code>id</code>.
   * The result is returned via the receiveResult method on the provided
   * Continuation with an Boolean represnting the result.
   *
   * @param c The command to run once the operation is complete
   * @param id The id of the object in question.
   * @return Whether or not an object is present at id.
   */
  public void exists(Id id, Continuation c) {
     c.receiveResult(new Boolean(idSet.isMember((Id) id))); 
  }

  /**
   * Returns the object identified by the given id.
   *
   * @param id The id of the object in question.
   * @param c The command to run once the operation is complete
   * @return The object, or <code>null</code> if there is no cooresponding
   * object (through receiveResult on c).
   */
  public void getObject(Id id, Continuation c){
      File objFile = getFile(id);
      if(objFile == null){
         c.receiveResult(null);
         return;
      }
      Object toReturn = null;
      try{ 
        toReturn = readData(objFile);
      }
      catch(Exception e){
        e.printStackTrace();
      }
      c.receiveResult(toReturn);
  }

  /**
   * Return the objects identified by the given range of ids. The array
   * returned contains the Comparable ids of the stored objects. The range is
   * completely inclusive, such that if the range is (A,B), objects with
   * ids of both A and B would be returned.
   *
   * Note that the two Comparable objects should be of the same class
   * (otherwise no range can be created).
   *
   * When the operation is complete, the receiveResult() method is called
   * on the provided continuation with a Comparable[] result containing the
   * resulting IDs.
   *
   * @param start The staring id of the range.
   * @param end The ending id of the range.
   * @param c The command to run once the operation is complete
   * @return The objects
   */
  public void scan(IdRange range , Continuation c) {
    Id startId;
    Id endId;
    try {
      startId = (Id) range.getCCW();
      endId = (Id) range.getCW();
    } catch (ClassCastException e) {
        c.receiveException(new IllegalArgumentException("start and end passed into scan are not id!"));
        return;
    }

    IdSet toReturn = idSet.subSet(startId, endId); 

    c.receiveResult(toReturn);
   }

  /**
   * Returns the total size of the stored data in bytes.The result
   * is returned via the receiveResult method on the provided
   * Continuation with an Integer representing the size.
   *
   * @param c The command to run once the operation is complete
   * @return The total size, in bytes, of data stored.
   */
  public void getTotalSize(Continuation c){
    c.receiveResult(new Long(usedSize));
  }


  /*****************************************************************/
  /* Functions for init/crash recovery                             */
  /*****************************************************************/

  /**
   * Perform all the miscealanious house keeping that must be done
   * when we start up
   */
  private void init(){
    if(! this.initDirectories())
      System.out.println("ERROR: Failed to Initialized Directories");

    if(numFilesDir(appDirectory) > MAX_FILES)
            expandDirectory(appDirectory);

    idSet = new IdSet();

    if(directoryTransactionInProgress()){
      directoryCleanUp(appDirectory);
    }
    initFileMap(backupDirectory);
  }

  /**
   * Verify that the directory name passed to the
   * PersistenceManagerImpl constructor is valid and
   * creates the necessary subdirectories.
   *
   * @return Whether the directories are successfully initialized.
   */
   private boolean initDirectories()
   {
    rootDirectory = new File(rootDir);
    if(createDir(rootDirectory) == false) {
      return false;
    }

    backupDirectory = new File(rootDirectory, backupDir);
    if (createDir(backupDirectory) == false) {
      return false;
    }

    appDirectory = new File(backupDirectory, getName());
    if (createDir(appDirectory) == false) {
      return false;
    }
    
    lostDirectory = new File(backupDirectory, "lost+found"); 
    if (createDir(lostDirectory) == false){
       return false;
    }
 
    return true;
   }


  /**
   * Inititializes the FileMap data structure
   * 
   * In doing this it must resolve conflicts and aborted
   * transactions. After this is run the most current stable
   * state should be restored
   *
   */
  private void initFileMap(File dir){
    File[] files = dir.listFiles();
    int numFiles = files.length;

    for ( int i = 0; i < numFiles; i++){
       /* insert keys into file Map */
       /* need to check for uncompleted and conflicting transactions */
       if(files[i].isFile()){

          try{
             
            long version = readVersion(files[i]);
            Object key = readKey(files[i]);

            if(!idSet.isMember((Id) key)){
              idSet.addMember((Id) key);
              increaseUsedSpace(files[i].length()); /* increase amount used */
            }
            else{
               /* resolve conflict due to unfinished trans */
              resolveConflict(files[i]);
              System.out.println("Resolving Conflicting Versions");
            }
          }
          catch(java.io.EOFException e){
              System.out.println("Recovering From Incomplete Write");
              moveToLost(files[i]);
          }
          catch(java.io.IOException e){
            System.out.println("Caught File Exception");
          }
          catch(Exception e){
            System.out.println("Caught OTHER EXCEPTION");
          }
       }
       else if(files[i].isDirectory()){
          initFileMap(files[i]);
       }
    }
  
  }

  /**
   * resolve a Conflict between two files that claim to have
   * the same key 
   *
   * Checks the version number and returns the newest one
   * and adjust acounting information correctly
   *
   * @param oldFile the File already mapped
   * @param newFile the conflicting file
   *
   * @return File the correct file
   *
   */
  private void resolveConflict(File conflictFile) throws Exception{
        //String id = conflictFile.getName().split(".")[0];
        //SubStringFilter ssf = new SubStringFilter(id);
        //File[] files = conflictFile.getParent().listFiles(ssf);
        //File correctFile = files[0];
        //for(int i = 1 ; i < files.length; i ++){
         //   if(getVersion(correctFile) < getVersion(files[i])){
          //     moveToLost(correctFile);
           //    correctFile = files[i];
           // }
           // else{
            //   moveToLost(files[i]); 
           // }
        //}
  }

  
  private void moveToLost(File file){
      File newFile = new File(lostDirectory, file.getName());
      file.renameTo(newFile);
  }
 
  /*****************************************************************/
  /* Helper functions for Directory  Transaction Management        */
  /*****************************************************************/

  /**
   * Creates a record saying there is a directory transaction
   * In progress.
   *
   * In reality creates a file which exists for the duration of
   * the time we are altering the directory. This allows us to
   * recover from a crash.
   *
   */ 
  private void beginDirectoryTransaction(){
    File f  = new File(backupDirectory, "dir-inprogress");
    try{
      f.createNewFile();
    }
    catch(IOException ioe){
      ioe.printStackTrace();
    }
  }

  /**
   * Destroys the record saying there is a directory transaction 
   * in progress.
   *
   * Removes the file which exists for the duration of the transaction.
   * 
   */
  private void endDirectoryTransaction(){
    File f  = new File(backupDirectory, "dir-inprogress");
    deleteFile(f);
  }

  /**
   * Checks to see if there is a transaction progress.
   *
   * Is used to check if we crashed in the middle of a directory
   * operation.
   *
   */
   private boolean directoryTransactionInProgress(){
    File f  = new File(backupDirectory, "dir-inprogress");
    return f.exists();
   }

   /**
    *
    * This cleans up if a directory operation is in progress if
    * when a crash occurs.
    *
    */
   private void directoryCleanUp(File dir){
     DirectoryFilter df = new DirectoryFilter();
     FileFilter ff = new FileFilter(); 
    
     File [] files = dir.listFiles(ff);
     File [] dirs = dir.listFiles(df);
     
     if(dirs.length == 0){
        /* this is the base case */ 
        /* if dir contains only files do nothing */
     }
     else if(dirs.length > 0){
        /* else if contains only dirs recurse */
        /* if contains both find appropriate places for files and move them*/
        /* then recurse */

        if(files.length > 0){
          moveFilesToCorrectDir(dir);
        }

        for(int i = 0 ; i < dirs.length; i++){
          directoryCleanUp(dir);         
        }

     } 
     endDirectoryTransaction();
   }

  private void expandDirectory(File dir){
     HashSet h = new HashSet();
     FileFilter ff = new FileFilter();
     String[] fileNames = dir.list(ff);

     for(int i = 0; i < fileNames.length; i++){
        if(dir.equals(appDirectory))
           h.add(fileNames[i].substring(0, 1));
        else
           h.add(fileNames[i].substring(0, dir.getName().length() + 1));
     }

     beginDirectoryTransaction(); 

     Iterator i = h.iterator();
     while(i.hasNext()){
       String newDir = (String) i.next(); 
       File newDirectory = new File(dir, newDir);
       createDir(newDirectory); 
     }

     moveFilesToCorrectDir(dir);

     endDirectoryTransaction();
  } 

  private void moveFilesToCorrectDir(File dir){
     FileFilter ff = new FileFilter();
     File[] files = dir.listFiles(ff);

     for(int i = 0; i < files.length; i++){
        File newFile = new File(getDirectoryForId(files[i].getName()), files[i].getName());
        files[i].renameTo(newFile);
     }

  }
   
  /*****************************************************************/
  /* Helper functions for File Management                          */
  /*****************************************************************/

  /**
   * Create a directory given its name
   *
   * @param directory The directory to be created
   * @return Whether the directory is successfully created.
   */
  private boolean createDir(File directory) {
    if (!directory.exists()) {
      directory.mkdir();
    }
    return directory.isDirectory();
  }

  private long getFileLength(File file){
   if (file == null)
      return 0;
   else 
      return file.length();
  }

  private void deleteFile(File file){
    if(file != null)
       file.delete();
  }
  /**
   * Generates a new file name to assign for a given id
   *
   * @param Comparable id the id to generate a name for
   * @return String the new File name
   *
   * This method will return the hashcode of the object used as the id
   * unless there is a collision, in which case it will return a random number
   * Since this mapping is only needed once it does not matter what number
   * is used to generate the filename, the hashcode is the first try for
   * effeciency.
   */
  private File makeFile(Id cid){
    Id id = (Id) cid;
    /* change here to put in right directory */
    Random rnd = new Random();
    File file = new File(getDirectoryForId(id.toStringFull()), id.toStringFull() + "." + rnd.nextInt() % 100);
    while(file.exists()){
      file = new File(getDirectoryForId(id.toStringFull()), id.toStringFull() + "." + rnd.nextInt() % 100);
    }
    return file;
  }

  private File getFile(Id id){
     File dir = getDirectoryForId(id.toStringFull());
     SubStringFilter ssf = new SubStringFilter(id.toStringFull());
     File[] results = dir.listFiles(ssf);
     if(results.length == 0)
        return null;
     else
        return results[0];
  }

  private File getDirectoryForId(String id){
    return getDirectoryForIdHelper(id, appDirectory);
  }

  private File getDirectoryForIdHelper(String id, File root){
      if(!containsDir(root)){
        return root; 
      }
      else{
        /* recurse and find the file */
        if(root == appDirectory)
             return getDirectoryForIdHelper(id, new File(root, id.substring(0, 1 )));
        else{
             File dir = new File(root, id.substring(0, root.getName().length() + 1));
             if(!dir.exists())
                createDir(dir);
             return getDirectoryForIdHelper(id, dir);
        }
      }
  }

  private int numFilesDir(File dir){
     FileFilter ff = new FileFilter();
     return dir.listFiles(ff).length;
  }

  private boolean containsDir(File dir){
    DirectoryFilter df = new DirectoryFilter();
    return( dir.listFiles(df).length != 0);
  }

  /*****************************************************************/
  /* Helper functions for Object Input/Output                      */
  /*****************************************************************/

  private static Serializable readObject(File file , int offset) throws Exception  {

    Serializable toReturn = null;
    if(file == null)
       return null;
    if(!file.exists())
       return null;

    FileInputStream fin;
    ObjectInputStream objin;
    synchronized (file) {
        fin = new FileInputStream(file);
        objin = new ObjectInputStream(fin);
        for(int i = 0 ; i < offset; i ++){
           objin.readObject(); /* skip objects */
        }
        toReturn = (Serializable) objin.readObject();
        fin.close();
        objin.close();
    }
    return toReturn;
  }


  /**
   * Abstract over reading a single object to a file using Java
   * serialization.
   *
   * @param file The file to create the object from.
   * @return The object that was read in
   */
  private static Serializable readData(File file) throws Exception{
     return(readObject(file, 1));
  }



  /**
   * Abstract over reading a single key from a file using Java
   * serialization.
   *
   * @param file The file to create the key from.
   * @return The key that was read in
   */
  private static Serializable readKey(File file) throws Exception{
    return (readObject(file, 0));
  }
     
    
  /**
   * Abstract over reading a version from a file using Java
   * serialization.
   *
   * @param file The file to create the version from.
   * @return The key that was read in
   */
  private static long readVersion(File file) throws Exception{
   long toReturn = 0;
   Long temp = ((Long) readObject(file, 2));
   if(temp != null){
     toReturn = temp.longValue();
   }
   return (toReturn);
  } 

  /**
   * Abstract over writing a single object to a file using Java
   * serialization.
   *
   * @param obj The object to be writen
   * @param file The file to serialize the object to.
   * @return The object's disk space usage
   */
   private static long writeObject(Serializable obj, Id key, long version, File file)     {
       if (obj == null || file == null)
            return 0;

       FileOutputStream fout;
       ObjectOutputStream objout;

       synchronized (file) {
          try {
              fout = new FileOutputStream(file);
              objout = new ObjectOutputStream(fout);
              objout.writeObject(key);
              objout.writeObject(obj);
              objout.writeObject(new Long(version));
              fout.close();
              objout.close();
          }
          catch (Exception e) {
             e.printStackTrace();
          }
          return file.length();
       }
    }

  /*****************************************************************/
  /* Functions for Configuration Management                        */
  /*****************************************************************/


  /**
   * Sets the root directory that the persistence Manager uses
   *
   * @param dir the String representing the directory to use
   * @return boolean, true if the operation suceeds false if it doesn't
   */
  public boolean setRoot(String dir) {
    /* We should do something logical here to the existing files */
    rootDir = dir;
    return true;
  }

  /**
   * gets the root directory that the persistence Manager uses
   *
   * @return String the directory for the root
   */
  public String getRoot() {
    return rootDir;
  }

  /**
   * gets the amount of storage that the persistence Manager uses
   *
   * @return int the amount of storage in MB allocated for use
   */
  public long getStorageSize() {
    return storageSize;
  }

  /**
   * Sets the amount of storage that the persistence Manager uses
   *
   * @param size the amount of storage available to use in MB
   * @return boolean, true if the operation suceeds false if it doesn't
   */
  public boolean setStorageSize(int size) {
    if(storageSize <= size){
        storageSize = size;
        return true;
    }
    else if( size > usedSize){
        storageSize = size;
        return true;
    }
    else {
       return false;
    }

  }
  
  /**
   * 
   * Increases the amount of storage recorded as used 
   *
   * @param long i the amount to increase usage by 
   */
  private void increaseUsedSpace(long i){
     usedSize = usedSize + i;
  }

  /**
   * 
   * decreases the amount of storage recorded as used 
   *
   * @param long i the amount to decrease usage by 
   */
  private void decreaseUsedSpace(long i){
     usedSize = usedSize - i;
  }

  private long getUsedSpace(){
    return usedSize;
  }

  private String getName(){
    return name;
  }

  private void debug(String s){
    if(DEBUG)
       System.out.println(s);
  }

/**********************************************************************/
  /*****************************************************************/
  /* Inner Classes for FileName filtering                          */
  /*****************************************************************/

  private class DirectoryFilter implements FilenameFilter{

   public boolean accept(File dir, String name){
     File temp = new File(dir, name);
     if(temp.isDirectory()){
        return true;
     }
     else{
       return false;
     }
   }
  }

  private class FileFilter implements FilenameFilter{

   public boolean accept(File dir, String name){
     File temp = new File(dir, name);
     if(temp.isDirectory()){
        return false;
     }
     else{
       return true;
     }
   }
  }

  private class SubStringFilter implements FilenameFilter{
    String s;

    public SubStringFilter(String s){
      this.s = s;    
    }

    public boolean accept(File dir, String Name){
      if(Name.startsWith(s)){
        return true;
      }
      else{
        return false;
      }
    }
  }
/**********************************************************************/
}


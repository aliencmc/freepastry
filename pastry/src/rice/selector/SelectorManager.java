
package rice.selector;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

import rice.environment.logging.*;
import rice.environment.logging.LogManager;
import rice.environment.time.TimeSource;

/**
 * This class is the class which handles the selector, and listens for activity.
 * When activity occurs, it figures out who is interested in what has happened,
 * and hands off to that object.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SelectorManager extends Thread implements Timer {

  // the maximal time to sleep on a select operation
  public static int TIMEOUT = 500;

  // The static selector manager which is used by all applications
  private static SelectorManager manager;

  // the underlying selector used
  protected Selector selector;

  // a list of the invocations that need to be done in this thread
  protected LinkedList invocations;

  // the list of handlers which want to change their key
  protected HashSet modifyKeys;

  // the list of keys waiting to be cancelled
  protected HashSet cancelledKeys;
  
  // the set used to store the timer events
  protected TreeSet timerQueue = new TreeSet();
  
  // the next time the selector is schedeled to wake up 
  protected long wakeupTime = 0;
  
  protected TimeSource timeSource;

  long lastTime = 0;

  protected LogManager log;
  
  protected String instance;
  
  /**
   * Constructor, which is private since there is only one selector per JVM.
   */
  public SelectorManager(boolean profile, String instance, TimeSource timeSource, LogManager log) {
    super(instance == null?"Selector Thread":"Selector Thread -- "+instance);
    this.instance = instance;
    this.log = log;
    this.invocations = new LinkedList();
    this.modifyKeys = new HashSet();
    this.cancelledKeys = new HashSet();
    this.timeSource = timeSource;
    
    // attempt to create selector
    try {
      selector = Selector.open();
    } catch (IOException e) {
      System.out.println("SEVERE ERROR (SelectorManager): Error creating selector " + e);
    }
    lastTime = timeSource.currentTimeMillis();
    start();
  }

  /**
   * Method which asks the Selector Manager to add the given key to the cancelled 
   * set.  If noone calls register on this key during the rest of this select() operation,
   * the key will be cancelled.  Otherwise, it will be returned as a result of the
   * register operation.
   *
   * @param key The key to cancel
   */
  public void cancel(SelectionKey key) {
    if (key == null)
      throw new NullPointerException();
    
    cancelledKeys.add(key);
  }
  
  /**
   * Utility method which returns the SelectionKey attached to the given channel, if 
   * one exists
   *
   * @param channel The channel to return the key for
   * @return The key
   */
  public SelectionKey getKey(SelectableChannel channel) {
    return channel.keyFor(selector);
  }

  /**
   * Registers a new channel with the selector, and attaches the given SelectionKeyHandler
   * as the handler for the newly created key.  Operations which the hanlder is interested
   * in will be called as available.
   *
   * @param channel The channel to regster with the selector
   * @param handler The handler to use for the callbacks
   * @param ops The initial interest operations
   * @return The SelectionKey which uniquely identifies this channel
   */
  public SelectionKey register(SelectableChannel channel, SelectionKeyHandler handler, int ops) throws IOException {
    if ((channel == null) || (handler == null))
      throw new NullPointerException();
    
    SelectionKey key = channel.register(selector, ops, handler);
    cancelledKeys.remove(key);
    
    return key;
  }

  /**
   * This method schedules a runnable task to be done by the selector thread
   * during the next select() call. All operations which modify the selector
   * should be done using this method, as they must be done in the selector
   * thread.
   *
   * @param d The runnable task to invoke
   */
  public synchronized void invoke(Runnable d) {
    if (d == null)
      throw new NullPointerException();
    
    invocations.add(d);
    selector.wakeup();
  }
  
  /**
   * Debug method which returns the number of pending invocations
   *
   * @return The number of pending invocations
   */
  public int getNumInvocations() {
    return invocations.size();
  }

  /**
   * Adds a selectionkey handler into the list of handlers which wish to change
   * their keys. Thus, modifyKeys() will be called on the next selection
   * operation
   *
   * @param key The key which is to be chanegd
   */
  public synchronized void modifyKey(SelectionKey key) {
    if (key == null)
      throw new NullPointerException();
    
    modifyKeys.add(key);
    selector.wakeup();
  }
  
  /**
   * This method is to be implemented by a subclass to do some task each loop.
   */
  protected void onLoop() {
  }

  /**
   * This method starts the socket manager listening for events. It is designed
   * to be started when this thread's start() method is invoked.
   */
  public void run() {
    try {
      //System.out.println("SelectorManager starting...");
      log(Logger.INFO, "SelectorManager -- "+instance+" starting...");

      lastTime = timeSource.currentTimeMillis();
      // loop while waiting for activity
      while (true) {        
        notifyLoopListeners();
        
        // NOTE: This is so we aren't always holding the selector lock when we get context switched 
        Thread.yield();
        executeDueTasks();
        onLoop();
        doInvocations();
        doSelections();
        synchronized(selector) {          
          int selectTime = SelectorManager.TIMEOUT;   
          if (timerQueue.size() > 0) {
            TimerTask first = (TimerTask)timerQueue.first(); 
            selectTime = (int)(first.nextExecutionTime - timeSource.currentTimeMillis());
          }
          
          select(selectTime);
          
          if (cancelledKeys.size() > 0) {
            Iterator i = cancelledKeys.iterator();
          
            while (i.hasNext())
              ((SelectionKey) i.next()).cancel();
          
            cancelledKeys.clear();
            
            // now, hack to make sure that all cancelled keys are actually cancelled (dumb)
            selector.selectNow();
          }
        }
      }
    } catch (Throwable t) {
      System.out.println("ERROR (SelectorManager.run): " + t);
      t.printStackTrace(System.out);
      System.exit(-1);
    }
  }
  
  protected void notifyLoopListeners() {
    long now = timeSource.currentTimeMillis();
    long diff = now - lastTime;
      // notify observers 
    synchronized(loopObservers) {
      Iterator i = loopObservers.iterator();
      while(i.hasNext()) {
        LoopObserver lo = (LoopObserver)i.next(); 
        if (lo.delayInterest() >= diff) {
          lo.loopTime((int)diff);
        }
      }
    }
    lastTime = now;
  }

  ArrayList loopObservers = new ArrayList();
  public void addLoopObserver(LoopObserver lo) {
    synchronized(loopObservers) {
      loopObservers.add(lo);
    }
  }
  
  public void removeLoopObserver(LoopObserver lo) {
    synchronized(loopObservers) {
      loopObservers.remove(lo);
    }     
  }
  
  
  protected void doSelections() throws IOException {
    SelectionKey[] keys = selectedKeys();

    for (int i = 0; i < keys.length; i++) {
      selector.selectedKeys().remove(keys[i]);

      synchronized(keys[i]) {
      SelectionKeyHandler skh = (SelectionKeyHandler) keys[i].attachment();

      if (skh != null) {
        // accept
        if (keys[i].isValid() && keys[i].isAcceptable()) {
          skh.accept(keys[i]);
        }

        // connect
        if (keys[i].isValid() && keys[i].isConnectable()) {
          skh.connect(keys[i]);
        }
        
        // read
        if (keys[i].isValid() && keys[i].isReadable()) {
          skh.read(keys[i]);
        }

        // write
        if (keys[i].isValid() && keys[i].isWritable()) {
          skh.write(keys[i]);
        }
      } else {
        keys[i].channel().close();
        keys[i].cancel();
      }
      }
    }
  }

  /**
   * Method which invokes all pending invocations. This method should *only* be
   * called by the selector thread.
   */
  protected void doInvocations() {
    Iterator i;
    synchronized (this) {
      i = new ArrayList(invocations).iterator();
      invocations.clear();
    }

    while (i.hasNext()) {
      Runnable run = (Runnable)i.next();
      try {
        run.run();
      } catch (Exception e) {
        System.err.println("Invoking runnable caused exception " + e + " - continuing");
        e.printStackTrace();
      }
    }

    synchronized (this) {
      i = new ArrayList(modifyKeys).iterator();
      modifyKeys.clear();
    }
    
    while (i.hasNext()) {
      SelectionKey key = (SelectionKey) i.next();
      if (key.isValid() && (key.attachment() != null))
        ((SelectionKeyHandler) key.attachment()).modifyKey(key);
    }
  }

  /**
   * Method which synchroniously returns the first element off of the
   * invocations list.
   *
   * @return An item from the invocations list
   */
  protected synchronized Runnable getInvocation() {
    if (invocations.size() > 0)
      return (Runnable) invocations.removeFirst();
    else
      return null;
  }

  /**
   * Method which synchroniously returns on element off
   * of the modifyKeys list
   *
   * @return An item from the invocations list
   */
  protected synchronized SelectionKey getModifyKey() {
    if (modifyKeys.size() > 0) {
      Object result = modifyKeys.iterator().next();
      modifyKeys.remove(result);
      return (SelectionKey) result;
    } else {
      return null;
    }
  }

  /**
   * Selects on the selector, and returns the result. Also properly synchronizes
   * around the selector
   *
   * @return DESCRIBE THE RETURN VALUE
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  int select(int time) throws IOException {
    if (time > TIMEOUT)
      time = TIMEOUT;
    
    try {      
      if ((time <= 0) || (invocations.size() > 0) || (modifyKeys.size() > 0)) 
        return selector.selectNow();
      
      wakeupTime = timeSource.currentTimeMillis() + time;
      return selector.select(time);
    } catch (IOException e) {
      if (e.getMessage().indexOf("Interrupted system call") >= 0) {
        System.out.println("Got interrupted system call, continuing anyway...");
        return 1;
      } else {
        throw e;
      }
    }
  }

  /**
   * Selects all of the keys on the selector and returns the result as an array
   * of keys.
   *
   * @return The array of keys
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private SelectionKey[] keys() throws IOException {
    return (SelectionKey[]) selector.keys().toArray(new SelectionKey[0]);
  }

  /**
   * Selects all of the currenlty selected keys on the selector and returns the
   * result as an array of keys.
   *
   * @return The array of keys
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected SelectionKey[] selectedKeys() throws IOException {
    return (SelectionKey[]) selector.selectedKeys().toArray(new SelectionKey[0]);
  }


  private void log(int loglevel, String s) {
    log.getLogger(SelectorManager.class, instance).log(loglevel, s);
  }

  /**
   * Returns whether or not this thread of execution is the selector 
   * thread
   *
   * @return Whether or not this is the selector thread
   */
  public static boolean isSelectorThread() {
    return (Thread.currentThread() == manager);
  }
  
  /**
   * Method which schedules a task to run after a specified number of millis
   *
   * @param task The task to run
   * @param delay The delay
   */
  public void schedule(TimerTask task, long delay) {
    task.nextExecutionTime = timeSource.currentTimeMillis() + delay;    
    addTask(task);
  }  
  
  /**
   * Method which schedules a task to run at a specified time
   *
   * @param task The task to run
   * @param time The time to run
   */
  public void schedule(TimerTask task, Date time) {
    task.nextExecutionTime = time.getTime();
    addTask(task);
  }
  
  /**
   * Method which schedules a task to run repeatedly after a 
   * specified delay and period
   *
   * @param task The task to run
   * @param delay The delay
   * @param period The period with which to run
   */
  public void schedule(TimerTask task, long delay, long period) {
    task.nextExecutionTime = timeSource.currentTimeMillis() + delay;
    task.period = (int) period;
    addTask(task);
  }
  
  /**
   * Method which schedules a task to run repeatedly first at a specified time 
   * and period
   *
   * @param task The task to run
   * @param firstTime The first time
   * @param period The period with which to run
   */  
  public void schedule(TimerTask task, Date firstTime, long period) {
    task.nextExecutionTime = firstTime.getTime();
    task.period = (int) period;
    addTask(task);
  }
  
  /**
   * Method which schedules a task to run repeatedly (at a fixed rate) after a 
   * specified delay and period
   *
   * @param task The task to run
   * @param delay The delay
   * @param period The period with which to run
   */
  public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
    task.nextExecutionTime = timeSource.currentTimeMillis() + delay;
    task.period = (int) period;
    addTask(task);
  }
  
  /**
   * Method which schedules a task to run repeatedly (at a fixed rate) after a 
   * specified delay and period
   *
   * @param task The task to run
   * @param delay The delay
   * @param period The period with which to run
   */  
  public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
    task.nextExecutionTime = firstTime.getTime();
    task.period = (int) period;
    addTask(task);
  }

  /**
   * Internal method which adds a task to the task tree, waking up the selector
   * if necessary to recalculate the sleep time
   *
   * @param task The task to add
   */
  private void addTask(TimerTask task) {
    synchronized(selector) {
      if (! timerQueue.add(task)) {
        System.out.println("ERROR: Got false while enqueueing task " + task + "!");
        Thread.dumpStack();
      }
    }
    
    // need to interrupt thread if waiting too long in selector    
    if (wakeupTime >= task.scheduledExecutionTime())
      selector.wakeup();
  }

  /**
   * Internal method which finds all due tasks and executes them.
   */
  protected void executeDueTasks() {
    //System.out.println("SM.executeDueTasks()");
    long now = timeSource.currentTimeMillis();
    ArrayList executeNow = new ArrayList();
    
    // step 1, fetch all due timers
    synchronized(selector) {
      boolean done = false;
      while(!done) {
        if (timerQueue.size() > 0) {
          TimerTask next = (TimerTask)timerQueue.first(); 
          if (next.nextExecutionTime <= now) {
            executeNow.add(next);
            //System.out.println("Removing:"+next);
            timerQueue.remove(next);          
          } else {
            done = true;
          }
        } else {
          done = true; 
        }
      }
    }
    
    
    // step 2, execute them all
    // items to be added back into the queue
    ArrayList addBack = new ArrayList();
    Iterator i = executeNow.iterator();
    while(i.hasNext()) {
      TimerTask next = (TimerTask)i.next(); 
      try {
        //System.out.println("SM.Executing "+next);
        if (next.execute(timeSource)) {
          addBack.add(next); 
        }
      } catch (Exception e) {
        e.printStackTrace(); 
      }
    }
    
    // step 3, add them back if necessary
    synchronized(selector) {
      i = addBack.iterator();
      while(i.hasNext()) {
        TimerTask tt = (TimerTask)i.next();
        //System.out.println("SM.addBack("+tt+")");
        timerQueue.add(tt);
      }
    }  
  }

  /**
   * Returns the timer associated with this SelectorManager (in this case, it is this).
   *
   * @return The associated timer
   */
  public Timer getTimer() {
    return this;
  } 
}

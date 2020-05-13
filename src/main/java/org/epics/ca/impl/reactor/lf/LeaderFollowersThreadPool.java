package org.epics.ca.impl.reactor.lf;

import org.epics.ca.util.logging.LibraryLogManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LF thread pool implementation.
 */
public class LeaderFollowersThreadPool
{
   private static final Logger logger = LibraryLogManager.getLogger( LeaderFollowersThreadPool.class );

   /**
    * Default thread pool size.
    */
   public static final int DEFAULT_THREADPOOL_SIZE = 5;

   /**
    * Shutdown status flag.
    */
   private final AtomicBoolean shutdown = new AtomicBoolean( false );

   /**
    * Executor.
    */
   private final ThreadPoolExecutor executor;

   /**
    * Constructor.
    */
   public LeaderFollowersThreadPool()
   {

      int threadPoolSize = DEFAULT_THREADPOOL_SIZE;
      String strVal = System.getProperty (this.getClass ().getName () + ".thread_pool_size", String.valueOf (threadPoolSize));
      if ( strVal != null )
      {
         try
         {
            // minimum are two threads (leader and one follower)
            threadPoolSize = Math.max (2, Integer.parseInt (strVal));
         }
         catch ( NumberFormatException nfe )
         { /* noop */ }
      }

      // NOTE: consider using LIFO ordering of threads (to maximize CPU cache affinity)
      // unbounded queue is OK, since its naturally limited (threadPoolSize + # of transports (used for flushing))
      executor = new ThreadPoolExecutor (threadPoolSize, threadPoolSize,
                                         Long.MAX_VALUE, TimeUnit.NANOSECONDS,
                                         new LinkedBlockingQueue<> ());
      executor.prestartAllCoreThreads ();
   }

   /**
    * Promote a new leader.
    *
    * @param task task to execute by a new leader.
    */
   public void promoteLeader( Runnable task )
   {
      //System.err.println("[promoteLeader] by " + Thread.currentThread().getName());
      execute (task);
   }

   /**
    * Execute given task.
    *
    * @param task task to execute.
    */
   public void execute( Runnable task )
   {
      try
      {
         executor.execute (task);
      }
      catch ( Throwable th )
      {
         /* noop */
         logger.log( Level.SEVERE, "Unexpected exception caught in one of the LF thread-pool thread.", th);
      }
   }

   /**
    * Shutdown.
    */
   public void shutdown()
   {
      if ( shutdown.get() )
      {
         return;
      }
      shutdown.set( true );
      executor.shutdown ();
      try
      {
         // NOTE: if thead pool is shutdown from one of its threads, this will always block for 1s
         if ( !executor.awaitTermination (1, TimeUnit.SECONDS) )
         {
            executor.shutdownNow();
         }
      }
      catch ( InterruptedException ie )
      { /* noop */ }
   }

}

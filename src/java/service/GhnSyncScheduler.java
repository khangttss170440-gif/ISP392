package service;

import dao.GhnDeliveryDAO;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletContext;

/** Application-owned workers; customer page requests never wait on the carrier API. */
public final class GhnSyncScheduler implements AutoCloseable {
    private final ServletContext context;
    private final ScheduledExecutorService clock;
    private final ThreadPoolExecutor workers;
    private final Set<Integer> queued=ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;
    public GhnSyncScheduler(ServletContext context) {
        this.context=context;
        AtomicInteger sequence=new AtomicInteger();
        ThreadFactory factory=job->{Thread t=new Thread(job,"mensform-ghn-"+sequence.incrementAndGet());t.setDaemon(true);return t;};
        clock=Executors.newSingleThreadScheduledExecutor(factory);
        workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(20),factory,new ThreadPoolExecutor.AbortPolicy());
    }
    public void start() {
        // Frequent light database scans avoid missing a due time by milliseconds and
        // accidentally waiting two minutes. Each order's database deadline is 60 seconds.
        clock.scheduleAtFixedRate(this::tick,5,5,TimeUnit.SECONDS);
        context.log("GHN background synchronization enabled: each active order every 60 seconds.");
    }
    private void tick() {
        if(stopped || !GhnClient.configured()) return;
        try {
            for(int order:new GhnDeliveryDAO().dueForSync(20)) {
                if(!queued.add(order)) continue;
                try {
                    workers.execute(()->{
                        try { if(!stopped) new GhnDeliveryDAO().syncScheduled(order); }
                        catch(Exception error) {
                            // The carrier may echo private data in errors. Log only local identity/type.
                            StackTraceElement[] trace=error.getStackTrace();
                            String origin=trace.length==0?"unknown":trace[0].toString();
                            context.log("GHN background sync deferred for order "+order+" ("+error.getClass().getSimpleName()+", origin="+origin+").");
                        } finally {queued.remove(order);}
                    });
                } catch(RejectedExecutionException busy) {queued.remove(order);}
            }
        } catch(Exception error) {
            context.log("GHN sync scan unavailable ("+error.getClass().getSimpleName()+"); retry next minute. Check the GHN sync database migration.");
        }
    }
    @Override public void close() {
        stopped=true;clock.shutdownNow();workers.shutdownNow();
        try {
            if(!workers.awaitTermination(10,TimeUnit.SECONDS)) context.log("GHN workers are still stopping; database leases will expire automatically.");
        } catch(InterruptedException e){Thread.currentThread().interrupt();}
        queued.clear();
    }
}

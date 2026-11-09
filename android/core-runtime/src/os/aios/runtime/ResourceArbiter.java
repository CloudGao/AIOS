package os.aios.runtime;

import java.util.*;

/** In-process lease projection. Durable owners must also persist the lease generation. */
public final class ResourceArbiter {
    public static final class Lease {public final String id,owner,resource;public final long generation,expiresAt;Lease(String i,String o,String r,long g,long e){id=i;owner=o;resource=r;generation=g;expiresAt=e;}}
    private final Map<String,Lease> leases=new HashMap<>();private long generation;
    public synchronized Lease acquire(String owner,String resource,long ttlMs,long now){
        if(ttlMs<1000||ttlMs>3600000)throw new IllegalArgumentException("Invalid lease duration");
        Lease old=leases.get(resource);if(old!=null&&old.expiresAt>now&&!old.owner.equals(owner))return null;
        Lease next=new Lease(UUID.randomUUID().toString(),owner,resource,++generation,now+ttlMs);leases.put(resource,next);return next;
    }
    public synchronized boolean valid(Lease lease,long now){Lease current=leases.get(lease.resource);return current!=null&&current.id.equals(lease.id)&&current.generation==lease.generation&&current.expiresAt>now;}
    public synchronized void release(Lease lease){Lease current=leases.get(lease.resource);if(current!=null&&current.id.equals(lease.id))leases.remove(lease.resource);}
    public synchronized void revokeOwner(String owner){leases.values().removeIf(v->v.owner.equals(owner));}
}


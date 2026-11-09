package os.aios.agent;
final class BackgroundExpiry {
 static final long STOPPED_MS=300000;
 static boolean due(long now,long stoppedSince){return stoppedSince>0&&now>=stoppedSince&&now-stoppedSince>=STOPPED_MS;}
}

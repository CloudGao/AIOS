package os.aios.agent;
public class UiObservationGeometryTest {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
 check(UiObservation.supplied("fresh","","").equals("fresh"));
 check(UiObservation.supplied("","fresh","").equals("fresh"));
 check(UiObservation.supplied("","","fresh").equals("fresh"));
 check(UiObservation.supplied("fresh","fresh","fresh").equals("fresh"));
 check(UiObservation.supplied("fresh","old","").isEmpty());
 check(UiObservation.supplied("fresh","","old").isEmpty());
 check(UiObservation.supplied("","fresh","old").isEmpty());
 check(UiObservation.supplied(null,"",null).isEmpty());
 check(!UiObservation.supplied("old","","").equals("fresh"));
 // Full 1080x2424 display: bottom app controls must not be clipped to app-window height.
 check(UiDisplayGeometry.contains(1080,2424,540,2310));
 check(UiDisplayGeometry.contains(1080,2424,1079,2423));
 check(!UiDisplayGeometry.contains(1080,2424,1080,2423));
 check(!UiDisplayGeometry.contains(1080,2424,540,2424));
 check(!UiDisplayGeometry.contains(1080,2424,-1,2310));
 check(UiDisplayGeometry.unchanged(1080,2424,0,1080,2424,0));
 check(!UiDisplayGeometry.unchanged(1080,2424,0,2424,1080,1));
 check(!UiDisplayGeometry.unchanged(1080,2424,0,1080,2424,2));
 check(!UiDisplayGeometry.unchanged(1080,2424,0,720,1616,0));
 System.out.println("PASS: "+checks+" observation alias and full-display geometry assertions");
 }
}

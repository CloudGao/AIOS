package os.aios.agent;
/** Coordinates are logical display pixels, including app areas next to system bars. */
final class UiDisplayGeometry {
 static boolean contains(int width,int height,int x,int y){return width>0&&height>0&&x>=0&&y>=0&&x<width&&y<height;}
 static boolean unchanged(int oldWidth,int oldHeight,int oldRotation,int width,int height,int rotation){return width>0&&height>0&&oldWidth==width&&oldHeight==height&&oldRotation==rotation;}
 private UiDisplayGeometry(){}
}

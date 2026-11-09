package os.aios.agent;

import org.json.*;

/** Geometry belongs to the observation credential, never to a model-supplied scale. */
final class UiObservationFrame {
    final int width,height,displayWidth,displayHeight;
    final String space;
    UiObservationFrame(JSONObject value){
        width=value.optInt("width");height=value.optInt("height");
        displayWidth=value.optInt("display_width",width);displayHeight=value.optInt("display_height",height);
        space=value.optString("coordinate_space","display_pixels");
        if(width<=0||height<=0||displayWidth<=0||displayHeight<=0)throw new IllegalArgumentException("Missing observation geometry");
    }
    static int[] imageSize(int width,int height){
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid display size");
        double scale=Math.min(1.0,2048.0/Math.max(width,height));
        return new int[]{Math.max(1,(int)Math.floor(width*scale)),Math.max(1,(int)Math.floor(height*scale))};
    }
    int x(int value){return map(value,width,displayWidth);}
    int y(int value){return map(value,height,displayHeight);}
    static int map(int value,int from,int to){
        if(value<0||value>=from)throw new IllegalArgumentException("Coordinate outside observed image");
        return Math.min(to-1,(int)Math.floor((value+0.5)*to/from));
    }
    JSONObject input(JSONObject request)throws Exception{
        if(request.has("coordinate_space")&&!space.equals(request.getString("coordinate_space")))
            throw new IllegalArgumentException("coordinate_space differs from observation; read again, do not guess a scale");
        JSONObject out=new JSONObject(request.toString());String op=out.optString("op");
        if(op.equals("tap")||op.equals("double_tap")||op.equals("long_press")){out.put("x",x(out.getInt("x")));out.put("y",y(out.getInt("y")));}
        if(op.equals("swipe")){out.put("x1",x(out.getInt("x1")));out.put("y1",y(out.getInt("y1")));out.put("x2",x(out.getInt("x2")));out.put("y2",y(out.getInt("y2")));}
        if(op.equals("gesture")){JSONArray pointers=out.getJSONArray("pointers");for(int i=0;i<pointers.length();i++){JSONArray path=pointers.getJSONObject(i).getJSONArray("path");for(int j=0;j<path.length();j++){JSONArray point=path.getJSONArray(j);point.put(0,x(point.getInt(0)));point.put(1,y(point.getInt(1)));}}}
        return out;
    }
    static void sanitize(Object value,int width,int height)throws Exception{
        if(value instanceof JSONArray){JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++)sanitize(list.get(i),width,height);return;}
        if(!(value instanceof JSONObject))return;JSONObject node=(JSONObject)value;JSONArray b=node.optJSONArray("bounds");
        if(b!=null&&b.length()==4){
            int l=Math.max(0,b.getInt(0)),t=Math.max(0,b.getInt(1)),rr=Math.min(width,b.getInt(2)),bb=Math.min(height,b.getInt(3));
            boolean valid=l<rr&&t<bb&&node.optBoolean("visible_to_user",true);
            node.put("bounds_valid",valid);
            if(valid)node.put("bounds",new JSONArray(new int[]{l,t,rr,bb}));
            else {node.put("raw_bounds",b);node.put("bounds",new JSONArray(new int[]{0,0,0,0}));node.put("clickable",false);}
        }
        if(node.has("children"))sanitize(node.get("children"),width,height);
        if(node.has("root"))sanitize(node.get("root"),width,height);
    }
}

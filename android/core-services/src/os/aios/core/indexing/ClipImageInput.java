package os.aios.core.indexing;
import android.graphics.Bitmap;

/** Separable antialiased bicubic shortest-side resize then center crop, RGB NCHW with the pinned OpenCLIP mean/std. */
public final class ClipImageInput {
    private static final int SIZE=224;
    private static final float[] MEAN={0.48145466f,0.4578275f,0.40821073f},STD={0.26862954f,0.26130258f,0.27577711f};
    private static double cubic(double x){x=Math.abs(x);if(x<1)return ((1.5*x-2.5)*x)*x+1;if(x<2)return ((-.5*x+2.5)*x-4)*x+2;return 0;}
    private static final class Filter {int start;float[] weights;Filter(int source,int target,int out){double scale=(double)source/target,center=(out+.5)*scale,support=2*Math.max(1,scale);start=Math.max(0,(int)(center-support+.5));int end=Math.min(source,(int)(center+support+.5));weights=new float[Math.max(0,end-start)];double sum=0;for(int i=0;i<weights.length;i++){weights[i]=(float)cubic((start+i+.5-center)/Math.max(1,scale));sum+=weights[i];}for(int i=0;i<weights.length;i++)weights[i]/=(float)sum;}}
    public static float[] encode(Bitmap bitmap){
        int width=bitmap.getWidth(),height=bitmap.getHeight();if(width<1||height<1||width*1L*height>17000000)throw new IllegalArgumentException("Decoded photo budget exceeded");
        int newWidth=width<=height?SIZE:(int)(SIZE*(double)width/height),newHeight=height<=width?SIZE:(int)(SIZE*(double)height/width);int left=(int)Math.rint((newWidth-SIZE)/2.0),top=(int)Math.rint((newHeight-SIZE)/2.0);
        Filter[] x=new Filter[SIZE],y=new Filter[SIZE];for(int i=0;i<SIZE;i++){x[i]=new Filter(width,newWidth,left+i);y[i]=new Filter(height,newHeight,top+i);}
        float[] horizontal=new float[height*SIZE*3];int[] row=new int[width];for(int h=0;h<height;h++){bitmap.getPixels(row,0,width,0,h,width,1);for(int col=0;col<SIZE;col++){Filter f=x[col];for(int k=0;k<f.weights.length;k++){int pixel=row[f.start+k],index=(h*SIZE+col)*3;horizontal[index]+=((pixel>>16)&255)*f.weights[k];horizontal[index+1]+=((pixel>>8)&255)*f.weights[k];horizontal[index+2]+=(pixel&255)*f.weights[k];}}}
        // Pillow RGB resize rounds/clamps the horizontal pass to 8-bit before the vertical pass.
        for(int i=0;i<horizontal.length;i++)horizontal[i]=Math.max(0,Math.min(255,Math.round(horizontal[i])));
        float[] output=new float[SIZE*SIZE*3];for(int h=0;h<SIZE;h++)for(int col=0;col<SIZE;col++)for(int channel=0;channel<3;channel++){Filter f=y[h];float value=0;for(int k=0;k<f.weights.length;k++)value+=horizontal[((f.start+k)*SIZE+col)*3+channel]*f.weights[k];output[channel*SIZE*SIZE+h*SIZE+col]=(Math.max(0,Math.min(255,Math.round(value)))/255f-MEAN[channel])/STD[channel];}return output;
    }
}

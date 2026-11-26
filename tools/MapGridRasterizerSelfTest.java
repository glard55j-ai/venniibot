import com.veniilocal.MapGridRasterizer;

public final class MapGridRasterizerSelfTest {
    private static void check(boolean yes, String what) { if (!yes) throw new AssertionError(what); }
    private static void rejects(byte[] cells, int w, int h) {
        try { MapGridRasterizer.rasterize(cells,w,h,600); throw new AssertionError("invalid grid accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        MapGridRasterizer.Preview small = MapGridRasterizer.rasterize(new byte[]{0,(byte)255,64,(byte)128},2,2,600);
        check(small.width==2 && small.height==2,"small dimensions");
        check(small.argb[0]==0xFF000000,"zero is black");
        check(small.argb[1]==0xFFFFFFFF,"255 is white");
        check(small.argb[2]==0xFF404040,"64 is grey");
        byte[] big = new byte[2000*2];
        big[0] = (byte) 255;
        MapGridRasterizer.Preview scaled = MapGridRasterizer.rasterize(big,2000,2,600);
        check(scaled.width==600 && scaled.height>=1,"scaled dimensions");
        check(scaled.argb.length<=600*600,"bounded raster");
        rejects(new byte[]{1,2,3},2,2);
        rejects(new byte[]{1},0,1);
        System.out.println("MapGridRasterizerSelfTest OK");
    }
}

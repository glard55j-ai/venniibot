import com.veniilocal.MapGridRasterizer;

/** All 4 orientations and 3 palettes are verified on synthetic, non-private data. */
public final class MapGridExplorerSelfTest {
    private static void ok(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        byte[] cells = {0,1,2,3,4,5}; // original: width=3 height=2
        MapGridRasterizer.Preview p0=MapGridRasterizer.rasterize(cells,3,2,600,0,0);
        ok(p0.width==3 && p0.height==2,"normal dims");
        ok(p0.sample(cells,0,0).value==0,"origin");
        ok(p0.sample(cells,2,1).value==5,"last cell");
        MapGridRasterizer.Preview p1=MapGridRasterizer.rasterize(cells,3,2,600,1,0);
        ok(p1.width==2 && p1.height==3,"rotated dims");
        ok(p1.sample(cells,0,0).value==3,"clockwise top-left");
        ok(p1.sample(cells,1,0).value==0,"clockwise top-right");
        ok(p1.sample(cells,0,2).value==5,"clockwise bottom-left");
        MapGridRasterizer.Preview p2=MapGridRasterizer.rasterize(cells,3,2,600,2,0);
        ok(p2.sample(cells,0,0).value==5,"180 top-left");
        MapGridRasterizer.Preview p3=MapGridRasterizer.rasterize(cells,3,2,600,3,0);
        ok(p3.sample(cells,0,0).value==2,"270 top-left");
        ok(p3.sample(cells,1,2).value==3,"270 bottom-right");
        ok(p3.sample(cells,-1,0)==null,"outside sample");
        byte[] b={(byte)0,(byte)255};
        MapGridRasterizer.Preview base=MapGridRasterizer.rasterize(b,2,1,600,0,0);
        MapGridRasterizer.Preview inverted=MapGridRasterizer.rasterize(b,2,1,600,0,1);
        MapGridRasterizer.Preview colorful=MapGridRasterizer.rasterize(b,2,1,600,0,2);
        ok(base.argb[0]==0xFF000000 && base.argb[1]==0xFFFFFFFF,"grayscale");
        ok(inverted.argb[0]==0xFFFFFFFF && inverted.argb[1]==0xFF000000,"inverse");
        ok(colorful.argb[0]!=base.argb[0],"false color differs");
        try { MapGridRasterizer.rasterize(cells,3,2,600,4,0); throw new AssertionError("bad rotation"); }
        catch (IllegalArgumentException expected) {}
        try { MapGridRasterizer.rasterize(cells,3,2,600,0,3); throw new AssertionError("bad palette"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("MapGridExplorerSelfTest OK");
    }
}

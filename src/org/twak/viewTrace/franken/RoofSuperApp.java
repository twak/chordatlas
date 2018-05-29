package org.twak.viewTrace.franken;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.camp.Output.Face;
import org.twak.camp.Tag;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.tweed.gen.skel.RoofTag;
import org.twak.utils.Imagez;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.Show;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.facades.HasApp;

public class RoofSuperApp extends SuperSuper <MiniRoof> implements HasApp {

	RoofTexApp parent;
	
	public Map<Tag, String> textures = null;

	
	public RoofSuperApp( RoofTexApp parent ) {
		
		super( parent );
		
		this.scale = 180;
		this.hasA = this;
		this.parent = parent;
	}

	public RoofSuperApp( RoofSuperApp o ) {

		super( (SuperSuper) o );

		this.scale = 180;
		this.parent = o.parent;
	}

	@Override
	public App copy() {
		return new RoofSuperApp( this );
	}

	public double[] getZFor( MiniRoof e ) {
		return e.app.zuper.styleZ;
	}

	@Override
	public void setTexture( MiniRoof mf,FacState<MiniRoof> state, String dest ) {
		
		if (textures == null)
			textures = new HashMap<>();
		
		mf.app.textureUVs = TextureUVs.ZERO_ONE;
		
		textures.put (state.tag, dest+".png");
	}
	
	private static class TwoRects {
		
		public DRectangle a, b;
		int res;
		
		public TwoRects (DRectangle a, DRectangle b, int res) {
			this.a = a;
			this.b = b;
			this.res = res;
		}
		
		public Point2d transform (Point2d in) {
			Point2d out = b.transform( a.normalize( in ) );
			out.set( out.x, res - out.y);
			return out;
		}
		
		public Point2d transform (Point3d in) {
			
			Point2d pt2 =Pointz.to2XY( in );
			
			Point2d out =  b.transform( a.normalize( pt2 ) );
			
			out.set( out.x, res - out.y);
			
			return out;
		}

		public Loop<Point2d> tranform( Loop<Point2d> verticalPts ) {
			
			return verticalPts.singleton().new Map<Point2d>() {
				@Override
				public Point2d map( Loopable<Point2d> input ) {
					return transform (input.get());
				}
			}.run().get(0);
		}
 	}
	
	@Override
	public void drawCoarse( MultiMap<MiniRoof, FacState> todo, MiniRoof mr ) throws IOException {
		
		BufferedImage src = ImageIO.read( Tweed.toWorkspace( parent.coarse ) );
		
//		DRectangle imageBounds = textureRect;
		
//		src = Imagez.blur( 25, src );
		
		
		NetInfo ni = NetInfo.get( parent );
		
		for ( Loop<Point2d> verticalPts : mr.getAllFaces() ) {
			
			TwoRects toPix = new TwoRects( mr.app.textureRect, new DRectangle(src.getWidth(), src.getHeight()), ni.resolution );
			
			Loop<Point2d> pixPts = toPix.tranform( verticalPts );
			
			Face origin = mr.origins.get( verticalPts );
			
			if (origin == null)
				continue;
			
			RoofTag rt = (RoofTag) GreebleHelper.getTag( origin.profile, RoofTag.class );
			
			Point2d start = toPix.transform( origin.edge.start ), 
					end   = toPix.transform( origin.edge.end );
			
			Line startEnd = new Line (start, end);
			
			AffineTransform 
					toOrigin  = AffineTransform.getTranslateInstance ( -start.x , -start.y ),
					rot       = AffineTransform.getRotateInstance    ( -startEnd.aTan2() ),
					deslope   = AffineTransform.getScaleInstance     ( 1, 1 / Math.cos ( origin.edge.getAngle() ) );
			
			AffineTransform t = AffineTransform.getTranslateInstance( 0, 0 ); 
			
			t.preConcatenate( toOrigin );
			t.preConcatenate( rot );
			t.preConcatenate( deslope );
			
			double[] bounds = Loopz.minMax2d( Loopz.transform( verticalPts, rot ) ); // bad location, but scale-in-meters.
			double[] pixBounds = Loopz.minMax2d( Loopz.transform( pixPts, t ) ); // bad location, but scale-in-meters.
			
			int 
			outWidth  =   (int) Math.ceil ( ( (bounds[1] - bounds[0] ) * scale ) / tileWidth ) * tileWidth, // round to exact tile multiples
			outHeight =   (int) Math.ceil ( ( (bounds[3] - bounds[2] ) * scale ) / tileWidth ) * tileWidth;
				
			BufferedImage bigCoarse = new BufferedImage(
					outWidth  + overlap * 2,
					outHeight + overlap * 2, BufferedImage.TYPE_3BYTE_BGR );

			Graphics2D g = bigCoarse.createGraphics();
			
			g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
			g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
	
			t.preConcatenate( AffineTransform.getScaleInstance ( outWidth / (pixBounds[1] - pixBounds[0] ), outHeight / (pixBounds[3] - pixBounds[2] ) ) );
			t.preConcatenate( AffineTransform.getTranslateInstance ( overlap, outHeight + overlap ) );
//			t.preConcatenate( AffineTransform.getTranslateInstance ( 256,256 ) );

			AffineTransform orig = g.getTransform();
			
			
			g.setTransform( t );
			
//			g.setColor( Color.magenta );
//			g.drawLine( (int) start.x, (int) start.y, (int) end.x, (int) end.y );
			
			g.drawImage (src, 0, 0, null);
			
			g.setTransform( orig );
//			g.fillRect( 256, 256, 100, 5 );
	
//			new Show( bigCoarse );
			
			g.dispose();
			
			
			FacState state = new FacState( bigCoarse, mr, new DRectangle(0,0,bounds[1] - bounds[0], bounds[3] - bounds[2] ), rt );
			
			for (int x =0; x <= outWidth / tileWidth; x ++)
				for (int y =0; y <= outHeight / tileWidth; y ++)
					state.nextTiles.add( new TileState( state, x, y ) );
	
			todo.put( mr, state );

//			break;
		}
	}

}

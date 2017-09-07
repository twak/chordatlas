package org.twak.tweed.gen;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import org.twak.tweed.Tweed;
import org.twak.tweed.gen.FeatureGen.MFPoint;
import org.twak.tweed.gen.FeatureGen.MegaFeatures;
import org.twak.utils.CloneSerializable;
import org.twak.utils.DumbCluster1D;
import org.twak.utils.Line;
import org.twak.utils.DumbCluster1D.Cluster;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaint;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.utils.ui.Plot;
import org.twak.utils.ui.Rainbow;
import org.twak.utils.PanMouseAdaptor;
import org.twak.viewTrace.SuperLine;
import org.twak.viewTrace.facades.MiniFacade;

import com.thoughtworks.xstream.XStream;

public class SolverState implements Serializable
{
	HalfMesh2 mesh;
	MultiMap<MegaFeatures, MFPoint> minis; 
	List<Prof> globalProfs;
	Map<SuperEdge, double[]> profFit;
	
	public StringBuilder dbgInfo = new StringBuilder(); 
	
	public SolverState( 
			HalfMesh2 mesh, 
			MultiMap<MegaFeatures, MFPoint> minis, 
			List<Prof> globalProfs, 
			Map<SuperEdge, double[]> profFit ) {

		this.mesh = mesh;
		this.minis = minis;
		this.globalProfs = globalProfs;
		this.profFit = profFit;
	}

	public SolverState copy( boolean removeMegaFacades ) {
		
		SolverState out = (SolverState) CloneSerializable.xClone( this );
		
		if (removeMegaFacades) {
			for (HalfFace f : out.mesh)
				for (HalfEdge e : f) {
					SuperEdge se = (SuperEdge)e;
					
					if (se.profLine != null)
						se.profLine.mega = null;
					
					if (e.over != null && ((SuperEdge)e.over).profLine != null) 
						((SuperEdge)e.over).profLine.mega = null;
				}
		}
	
		
		return out;
	}
		
	public void debugSolverResult() {
		new Plot( mesh ).add ( miniPainter() ).add (new ICanPaint() {
			@Override
			public void paint( Graphics2D g, PanMouseAdaptor ma ) {
				
				Set<HalfEdge> seen = new HashSet<>();
				Color tWhite = new Color(255,255,255,150);
				if (!seen.isEmpty())
				for (HalfFace f : mesh) {
					for (HalfEdge e : f) {
						int i = ((SuperEdge)e).profI;
//						if (i == -1)
//							continue;
						Point2d loc = e.line().fromPPram( 0.5 );
						String s = ""+i;
						
						int offset = e.start.x < e.end.x ? -10 : 10;
						seen.add(e);
						
						Rectangle2D b = g.getFontMetrics().getStringBounds( s, g );
						
						b = new Rectangle2D.Double(0,0, b.getWidth()+ 4, b.getHeight() );
						
						g.setColor(tWhite);
						g.fillRect(ma.toX( loc.x ) - (int) ( b.getWidth()/2), offset+ ma.toY(loc.y ) - (int)(b.getHeight()/2), 
								(int)(b.getWidth()), (int)(b.getHeight()));
						
						g.setColor(Color.gray);
						g.drawString( s, ma.toX( loc.x ) - (int) ( b.getWidth()/2)+2, offset+ma.toY(loc.y ) + (int)(b.getHeight()/2) - 3);
					}
				}
			}} );
	}

	public ICanPaint miniPainter() {
		return new ICanPaint() {

			@Override
			public void paint( Graphics2D g, PanMouseAdaptor ma ) {

				//			for ( MegaFeatures f : SS.minis.keySet() ) {
				//				
				//				for ( MFPoint mfp : SS.minis.get( f ) ) {
				//					Line l = mfp.image.mega.megafacade;
				//					
				//					spreadImages( g, ma, l, mfp,  Listz.from( mfp.left, mfp.right ) );
				//					
				//				}
				//			}

				//			if (SS.minis == null)
				
				int brake = 100;
				
				for ( HalfFace f : mesh ) {
					for ( HalfEdge e : f ) {

						if ( e.over != null )
							continue;

						if (brake-- < 0)
							break;
						
						SuperEdge se = (SuperEdge) e;
						if ( se.mini == null )
							continue;
						List<MiniFacade> mfs = new ArrayList( se.mini );
						//					while (mfs .size() < 2)
						//						mfs.add(null);
						
						spreadImages( g, ma, se.line(), se.line().fromPPram( 0.5 ), mfs );
					}
				}

				int i = 0;

				if ( minis != null )
					for ( MegaFeatures f : minis.keySet() ) {

						//				PaintThing.paint (f.megafacade, g, ma);

						DumbCluster1D<MFPoint> res = SkelSolver.clusterMinis( f, minis );

						Vector2d dir = f.megafacade.dir();

						Vector2d out = new Vector2d( dir );
						out.set( -out.y, out.x );
						out.scale( ma.toZoom( 2 ) / out.length() );

						for ( Cluster<MFPoint> c : res ) {

							g.setColor( Rainbow.getColour( i++ ) );

							for ( MFPoint mfp : c.things ) {

								Point2d pt = new Point2d( mfp );
								pt.add( out );

								g.setStroke( new BasicStroke( 0.2f ) );
								PaintThing.paint( pt, g, ma );

								g.setStroke( new BasicStroke( 0.2f ) );
								for ( HalfEdge e : SkelSolver.findNear( f.megafacade, mfp, mesh ) )
									g.drawLine( ma.toX( pt.x ), ma.toY( pt.y ), ma.toX( e.end.x ), ma.toY( e.end.y ) );

								if ( mfp.selectedEdge != null ) {
									g.setStroke( new BasicStroke( 2f ) );
									g.drawLine( ma.toX( pt.x ), ma.toY( pt.y ), ma.toX( mfp.selectedEdge.end.x ), ma.toY( mfp.selectedEdge.end.y ) );
								}
							}
						}
					}
				g.setStroke( new BasicStroke( 1 ) );
			}

			private void spreadImages( Graphics2D g, PanMouseAdaptor ma, Line sel, Point2d cen, List<MiniFacade> mfs ) {
				Vector2d perp = sel.dir();
				perp.set( -perp.y, perp.x );
				perp.normalize();

				for ( int i = 0; i < mfs.size(); i++ ) {

					MiniFacade mf = mfs.get( i );

					Vector2d p2 = new Vector2d( perp );
					p2.scale( ( i + 1 ) * 10 );

					p2.add( cen );

					if ( mf == null ) {
						g.setColor( Color.black );
						g.fillRect( ma.toX( p2.x - 1 ), ma.toY( p2.y - 3 ), ma.toZoom( 2 ), ma.toZoom( 6 ) );
						continue;
					}

					double w = mf.width * 0.1;
					double h = mf.height * 0.1;

					mf.paintImage( g, ma, p2.x - w, p2.y - h, p2.x + w, p2.y + h );
				}
			}
		};
	}
	
	public void save( File location, boolean clean ) {
		try {

			if ( clean )
				for ( HalfFace f : mesh ) {
					for ( HalfEdge e : f ) {
						SuperEdge se = (SuperEdge) e;
						if ( se.profLine != null )
							( (SuperLine) se.profLine ).mega = null;
					}
				}

			System.out.print( "writing state to " + location +" ..." );
   		    location.getAbsoluteFile().getParentFile().mkdirs();
			new XStream().toXML( this, new FileOutputStream( location ) );
			System.out.println( "done!" );
		} catch ( FileNotFoundException e ) {
			e.printStackTrace();
		}
	}
}
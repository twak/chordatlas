package org.twak.viewTrace;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;

import javax.vecmath.Point2d;

import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.HalfMesh2;
import org.twak.utils.HalfMesh2.HalfEdge;
import org.twak.utils.HalfMesh2.HalfFace;
import org.twak.utils.Line;
import org.twak.utils.MUtils;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.Rainbow;

public class SuperMeshPainter implements ICanPaintU {
		@Override
		public void paint( Object oa, Graphics2D g2, PanMouseAdaptor ma ) {

			HalfMesh2 o = (HalfMesh2) oa;

			double scatterRadius = 0.0;
			
			if (o.faces.isEmpty())
				return;
			
			double maxHeight = o.faces.stream().mapToDouble( x -> ((SuperFace)x).height ).max().getAsDouble();
			int fc= 0;// (int) ( Math.random() * 100 );
			
			for ( HalfFace f : o.faces ) {

				fc++;
				
				Polygon pwt = new Polygon();

			try {
				for ( HalfEdge e : f.edges() ) {
					pwt.addPoint( ma.toX( e.start.x + Math.random() * scatterRadius ), ma.toY( e.start.y + Math.random() * scatterRadius ) );
					//					PaintThing.paint(e.start, g2, ma);

				}
			} catch ( Throwable t ) {
				t.printStackTrace();
			}

				SuperFace sf = (SuperFace)f;

				Color c;
				
				if (false) {
					int h = (int) MUtils.clamp( ( (SuperFace) f ).height * 5, 0, 255 );
					
					if ( ( (SuperFace) f ).height == -Double.MAX_VALUE)
						c = Color.green;
					else if ( ( (SuperFace) f ).height < 0)
						c = Color.red;
					else
						c = new Color( h, h, h );
					
				}
				else if (sf.height == -Double.MAX_VALUE) {
					c = Color.yellow;
				}
				else if (sf.classification == -1 || sf.height < 0) {
					c = Color.red;
				}
				else {
//					c = Color.white;
					c = Rainbow.getColour( sf.classification + 1 );
//					c = Rainbow.getColour( fc++ + 1 );
				}
				
//				c = Color.white;
				
				g2.setColor(c);// new Color( c.getRed(), c.getGreen(), c.getBlue(), 50 ) );
				g2.fill( pwt );
				
//				Loop<Point2d> pts = new Loop<>();
//				for ( HalfEdge e : f.edges() ) 
//					pts.append(e.end);
//				
//				if ( ( Loopz.area( pts ) ) < 0.1 ) {
//					g2.setColor(Color.red);
//					g2.setStroke( new BasicStroke( 4f ) );
//					g2.draw( pwt );
//				}
			}
			
			for ( HalfFace f : o.faces ) {
				
				g2.setColor(Color.black	);
				
				try {
				for (HalfEdge e : f) {

					SuperEdge se = (SuperEdge)e;
					
					g2.setColor( Color.black );
					if (se.proceduralFacade != null) {
						g2.setStroke( new BasicStroke( 3f ) );
					}
					else
					{
						g2.setStroke( new BasicStroke( 1f ) );
					}
					
//					if ( se.profLine != null || ( se.over != null && ((SuperEdge)se.over).profLine != null ) ) {
//						g2.setStroke( new BasicStroke( 2f ) );
//						g2.setColor( new Color (255, 0, 0 ) );
//					}
//					else
//					{
//						g2.setStroke( new BasicStroke( 1f ) );
//						g2.setColor( Color.black );
//					}
//					g2.setColor( ((SuperEdge)e).profLine == null? Color.green : Color.magenta);
						PaintThing.paint ( e.line(), g2, ma );
//					}
				}
				}
				catch (Throwable th) {
					th.printStackTrace();
				}
				
				g2.setStroke( new BasicStroke( 3 ) );
				
				if (false)
				for ( HalfEdge e : f.edges() ) {
					
					SuperEdge se = (SuperEdge)e;
					if ( se.profLine != null ) {
//						g2.setColor( Color.BLACK );

						Line l = se.line();// new Line (new Point2d (e.start), new Point2d (e.end));
						
//						PaintThing.drawArrow( g2, ma, l, 5 );
						
						g2.drawLine( ma.toX (l.start.x), ma.toY (l.start.y),
								ma.toX (l.end.x), ma.toY (l.end.y) );
					}
					
					
//					if (e.over != null)
//					{
//						l.moveLeft( ma.fromZoom( 2 ) );
//						
//						double delta = Math.abs (  ((SuperEdge)e).localHeight - ((SuperEdge)e.over).localHeight );
//						
//						int h =  (int) Math.min(255, delta * 10 ) ;
//						g2.setColor ( new Color (0,h,h) );
//						g2.setStroke (new BasicStroke( 3f ));
//						
//					}
					
//				if ( ( (SuperEdge) e ).debug ) {
//				if ( e.over == null || ((SuperFace)e.over.face).classification != ((SuperFace)f).classification ) {
//					g2.setColor( Color.black );
//					g2.setStroke( new BasicStroke( 2 ) );
//					PaintThing.paint( e.line(), g2, ma );
//				}
				}
			}
			
			
			fc = 0;
			if (false)
			for ( HalfFace f : o.faces ) {
				
//				Color c = Rainbow.getColour( fc++ );
//				g2.setColor(new Color( c.getRed(), c.getGreen(), c.getBlue(), 150 ) );
				
//				g2.setColor( new Color(0,0,0,20) );
				
//				Point2d off = new Point2d(Math.random() -0.5, Math.random()-0.5) ;
				
				for ( HalfEdge e : f.edges() ) {
					
//					Point2d pt = new Point2d(e.start);
//					pt.add( off );
//					Point2d pt2 = new Point2d(e.end);
//					pt2.add( off );
//					
//					PaintThing.paint( new Line (pt, pt2) , g2, ma );
					g2.setColor( new Color(255,0,0,20) );
					if (e.line().absAngle( e.next.line() ) > Math.PI - 0.001) {
						
						g2.setColor( new Color(0,255,0,255) );
					}
					
					PaintThing.paint( new Line (e.start, e.end) , g2, ma );
					PaintThing.paint( new Line (e.next.start, e.next.end) , g2, ma );
					
					
				}

//				for ( HalfEdge e : f.edges() ) {
//					PaintThing.drawArrow( g2, ma, e.line(), 5 );
//				}
			}

			if (false)
			for ( HalfFace f : o.faces )
				for ( HalfEdge e : f.edges() ) {
					if ( 
							e.face != f || 
						  ( e.over != null && ( e.over.over != e || !o.faces.contains( e.over.face ) ) ) || 
							e.face == null || e.start == null || e.end == null || e.next == null ) {
						g2.setColor( Color.red );
						g2.setStroke( new BasicStroke( 4 ) );
						PaintThing.paint( e.line(), g2, ma );
					}
				}
			

		}
	}
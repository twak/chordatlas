package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.siteplan.jme.MeshBuilder;
import org.twak.utils.Line;
import org.twak.utils.MUtils;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.Tube.CrossGen;

public class GreebleEdge {
	
	public static void roowWallGreeble (Output output, MeshBuilder roof, MeshBuilder wall) {
		
		MultiMap<Point3d, SharedEdge> boundary = new MultiMap<>();
		
		Set<SharedEdge> roofWallBoundary = new HashSet<>();
		
		for (Face f1 : output.faces.values()) 
			for ( Loop<SharedEdge> sl : f1.edges ) 
				for ( Loopable<SharedEdge> se : sl.loopableIterator() ) {
					Face f2 = se.get().getOther(f1);
					if (isWall(f1) ^ isWall(f2))  {
						boundary.put(se.get().getStart(f1), se.get());
						roofWallBoundary.add(se.get());
					}
				}
		
			for (SharedEdge se : roofWallBoundary ) {
				
				if (se.start.z < 1 || se.end.z < 1)
					continue; // something strange here...
				
				Face f1 = se.left,
					 f2 = se.right;

				if (f1 == null || f2 == null)
					continue;
				
				EdgePoint
					bs = findAdjacent(se.start, se.end  , boundary),
					be = findAdjacent(se.end  , se.start, boundary);
				
				if (bs != null && be != null) {
					
					LinearForm3D cutS, cutE; 
					
					if ( isOverhang( bs.se ) ) 
						cutS = cut (bs.pt, se.start, se.end); // between two angles
					else
						cutS = new LinearForm3D( LinearForm3D.linePerp ( toXZ( se.end ), toXZ( se.start ) )  ); // flat at end
					
					if ( isOverhang( be.se ))
						cutE = cut (se.start, se.end, be.pt);
					else
						cutE = new LinearForm3D( LinearForm3D.linePerp ( toXZ( se.start ), toXZ( se.end ) )  ); // flat at end
					
					LinearForm3D 
						right  = new LinearForm3D( toXZ( f1.edge.getPlaneNormal() ), toXZ( f1.edge.start ) ),
						left   = new LinearForm3D( toXZ( f2.edge.getPlaneNormal() ), toXZ(f2.edge.start ) );
					
					
					Tube.tube(roof, Collections.singleton(cutS), 
							Collections.singleton(cutE), 
							new Line3d( toXZ ( se.start ), toXZ (  se.end) ), 
							 left, right, isOverhang(se) ? new OverhangCross() : new LipCross() );
				}
			}
	}
	
	private static boolean isOverhang (SharedEdge se) {
		
		Face f1 = se.left,
				 f2 = se.right;

		double angle = f1.edge.getPlaneNormal().angle(f2.edge.getPlaneNormal());
		
		return angle < Math.PI * 0.4;
	}
	
	
	private static class OverhangCross implements CrossGen {

		@Override
		public List<Point2d> gen(Vector2d left, Vector2d right) {

			Point2d overhang = new Point2d(right);
			overhang.scale(0.2);

			Line l = new Line(new Point2d(), new Point2d(left));
			Point2d r = l.project(overhang, false);

			List<Point2d> out = new ArrayList<>();

			out.add(new Point2d());
			out.add(overhang);
			out.add(r);

			return out;
		}
	}
	
	private static class LipCross implements CrossGen {
		
		@Override
		public List<Point2d> gen(Vector2d left, Vector2d right) {
			
			Vector2d l = new Vector2d(left);
			l.normalize();
			Vector2d lP = new Vector2d(l.y, -l.x );
			
			Vector2d r = new Vector2d(right);
			r.normalize();
			Vector2d rP = new Vector2d(-r.y, r.x );
	
			List<Point2d> out = new ArrayList();
	
			double width = 0.15, height = 0.03;
			
			Vector2d rn = new Vector2d(r);
			rn.negate();
			double cenOffset = 0.02;// height / Math.sin ( ( Math.PI - l.angle( rn ) ) /2 ); 
			
			Vector2d cen = new Vector2d( lP );
			cen.add(rP);
			cen.normalize();
			
					
			for (double[] coords : new double[][] {
				{0,      0,  0, 0,   0   },
				{0,      0,  0, 0,   -width},
				{0,      0,  0, height, -width},
				{0,      0,   cenOffset ,0, 0},
				{-width, height,0, 0,   0   },
				{-width, 0, 0,  0,   0   },
			} ) {
				
				Point2d tmp = new Point2d(l), tmp2;
				tmp.scale (coords[0]);
				
				tmp2 = new Point2d( lP );
				tmp2.scale (coords[1]);
				tmp.add(tmp2);
				
				tmp2 = new Point2d( cen );
				tmp2.scale (coords[2]);
				tmp.add(tmp2);
				
				tmp2 = new Point2d( rP );
				tmp2.scale (coords[3]);
				tmp.add(tmp2);
				
				tmp2 = new Point2d( r );
				tmp2.scale (coords[4]);
				tmp.add(tmp2);
		
				out.add(tmp);
			}
	
			return out;
		}
	}

	
	
	private static LinearForm3D cut(Point3d a, Point3d b, Point3d c) {
		
		Vector3d ab = new Vector3d(b);
		ab.sub(a);
		Vector3d bc = new Vector3d(c);
		bc.sub(b);
		
		ab.normalize();
		bc.normalize();
		
//		if ( true || ab.z > 0.0 || bc.z > 0.0) {
			ab.add( bc );
			ab.normalize();
			
			return new LinearForm3D( toXZ( ab ), toXZ( b ) );
//		}
//		Vector2d ab2 = new Vector2d( ab.x, ab.y ),
//				bc2 = new Vector2d( bc.x, bc.y );
//		
//		ab2.normalize();
//		bc2.normalize();
//		
//		ab2.add( bc2 );
//		
//		Vector3d normal = new Vector3d(ab2.x , ab2.y, 0);
//		normal.normalize();
//		
//		return new LinearForm3D( toXZ( normal ), toXZ( b ) );
	}

	public static Vector3d toXZ (Vector3d xy) {
		return new Vector3d( xy.x, xy.z, xy.y );
	}
	public static Point3d toXZ (Point3d xy) {
		return new Point3d( xy.x, xy.z, xy.y );
	}
	
	private static class EdgePoint {
		SharedEdge se;
		Point3d pt;
		
		public EdgePoint (SharedEdge se, Point3d pt) {
			this.se = se;
			this.pt = pt;
		}
	}
	
	private static EdgePoint findAdjacent(Point3d start, Point3d not, MultiMap<Point3d, SharedEdge> boundary ) {
		
		for (SharedEdge p : boundary.get(start)) {
			if ( p.start.equals(start) && !p.end.equals(not) )
				return new EdgePoint( p, p.end );
			if ( p.end.equals(start) && !p.start.equals(not ) )
				return new EdgePoint( p, p.start );
		}
		return null;
	}

	public static boolean roofGreeble( Face f, MeshBuilder roof ) {

		boolean isWall = isWall( f );

		for ( Loop<SharedEdge> sl : f.edges ) {

			for ( Loopable<SharedEdge> sel : sl.loopableIterator() ) {

				SharedEdge se = sel.get();
				boolean otherIsWall = isWall( se.getOther( f ) );

				if ( !isWall && !otherIsWall ) {

					Face oF = se.getOther( f );
					
					if (oF == null || order (f, oF) || f.edge.getPlaneNormal().angle( oF.edge.getPlaneNormal() ) < 0.4 )
						continue;
					

					List<LinearForm3D> 
							start  = new ArrayList(), 
							end    = new ArrayList<>();
					
					{
						SharedEdge 
							fPrev  = se.getAdjEdge( f , false ),
							fNext  = se.getAdjEdge( f , true  ), 
							ofPrev = se.getAdjEdge( oF, false ),
							ofNext = se.getAdjEdge( oF, true  );
					
						
						if (fNext == null || fPrev == null || ofNext == null || ofPrev == null)
							continue;
						
						double overHang = -0.03;

						if ( isWall( fNext.getOther( f ) ) )
							end.add( toLF( fNext.getOther( f ), overHang ) );
						else
							end.add( roofTween( fNext, se, f ) );

						if ( isWall( ofPrev.getOther( oF ) ) )
							end.add( toLF( ofPrev.getOther( oF ), overHang ) );
						else
							end.add( roofTween( se, ofPrev, oF ) );

						if ( isWall( fPrev.getOther( f ) ) )
							start.add( toLF( fPrev.getOther( f ), overHang ) );
						else
							start.add( roofTween( se, fPrev, f ) );

						if ( isWall( ofNext.getOther( oF ) ) )
							start.add( toLF( ofNext.getOther( oF ), overHang ) );
						else
							start.add( roofTween( ofNext, se, oF ) );
					}
					
					Point3d s = se.getStart( f ), e = se.getEnd(f);
					
					
					Tube.tube( roof, end, start,
							new Line3d( new Point3d (s.x, s.z, s.y), new Point3d (e.x, e.z, e.y) )
							, toLF (f, 0) , toLF(oF, 0), new CrossGen() {
								
								@Override
								public List<Point2d> gen( Vector2d left, Vector2d right ) {
									
									Vector2d l = new Vector2d(left);
									l.normalize();
									Vector2d lP = new Vector2d(l.y, -l.x );
									
									Vector2d r = new Vector2d(right);
									r.normalize();
									Vector2d rP = new Vector2d(-r.y, r.x );
							
									List<Point2d> out = new ArrayList();
							
									double width = 0.15, height = 0.03;
									
									Vector2d rn = new Vector2d(r);
									rn.negate();
									double cenOffset = 0.02;// height / Math.sin ( ( Math.PI - l.angle( rn ) ) /2 ); 
									
									Vector2d cen = new Vector2d( lP );
									cen.add(rP);
									cen.normalize();
									
											
									for (double[] coords : new double[][] {
										{0,      0,  0, 0,   0   },
										{0,      0,  0, 0,   -width},
										{0,      0,  0, height, -width},
										{0,      0,   cenOffset ,0, 0},
										{-width, height,0, 0,   0   },
										{-width, 0, 0,  0,   0   },
									} ) {
										
										Point2d tmp = new Point2d(l), tmp2;
										tmp.scale (coords[0]);
										
										tmp2 = new Point2d( lP );
										tmp2.scale (coords[1]);
										tmp.add(tmp2);
										
										tmp2 = new Point2d( cen );
										tmp2.scale (coords[2]);
										tmp.add(tmp2);
										
										tmp2 = new Point2d( rP );
										tmp2.scale (coords[3]);
										tmp.add(tmp2);
										
										tmp2 = new Point2d( r );
										tmp2.scale (coords[4]);
										tmp.add(tmp2);
								
										out.add(tmp);
									}
							
									return out;
								}
							} );
				}
			}
		}
		
		return true;
	}
	
	private static boolean order( Face a, Face b ) {
		
		Point3d ap = a.definingSE.iterator().next().getStart( a );
		Point3d bp = b.definingSE.iterator().next().getStart(b);
		
		if (ap == null || bp == null)
			return true; //?!
		
		if (ap.x < bp.x)
			return true;
		if (ap.x > bp.x)
			return false;
		
		if (ap.y < bp.y)
			return true;
		if (ap.y > bp.y)
			return false;
		
		return false;
	}

	private static LinearForm3D toLF( Face f, double tol ) {
		
		if (f == null)
			return null;
		
		Vector3d normal = f.edge.getPlaneNormal();
		normal = new Vector3d( normal.x, normal.z, normal.y );
		
		Point3d thro = f.definingSE.iterator().next().getStart( f );
		thro = new Point3d( thro.x, thro.z, thro.y );
		
		LinearForm3D out = new LinearForm3D( normal, thro );
		
		out.D += tol;
		
		return out;
	}

	private static LinearForm3D roofTween( SharedEdge a, SharedEdge b, Face f ) {
		
		if (a == null || b == null)
			return null;
		
		Vector3d aD = a.dir(f);
		
		if (aD == null)
			return null;
		
		aD.normalize();
		
		Vector3d bD = b.dir(f);
		
		if (bD == null)
			return null;
		
		bD.normalize();
		
		aD.add(bD);
		
		aD.normalize();
		
		Point3d pt = a.getEnd( f );
		
		return new LinearForm3D( new Vector3d(aD.x, aD.z, aD.y), new Point3d(pt.x, pt.z, pt.y)  );
	}

	private static boolean isWall (Face face) {
		
		return face != null && face.profile.stream().anyMatch( x -> x instanceof WallTag );
	}
	
	
//	private Node dbgViz( List<LinearForm3D> start, Point3d s ) {
//		Node dN = new Node();
//		for (LinearForm3D lf3 : start) {
//			Cylinder dbg = new Cylinder(4, 4, 0.03f, 0, 0.3f, true , false);
//			Geometry gD = new Geometry ("dbg", dbg);
//			
//			Transform trans = new Transform();
//			
//			Quaternion q = new Quaternion();
//			q.lookAt( new Vector3f( (float) lf3.A, (float) lf3.B, (float) lf3.C), new Vector3f(0,1,0) );
//			trans.setRotation( q );
//			trans.setTranslation( new Vector3f( (float) s.x, (float) s.z, (float) s.y ) );
//			
//			
//			
//			gD.setLocalTransform(trans);
//			
//			Material mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
//			mat.setColor( "Color", ColorRGBA.Cyan);
//			gD.setMaterial( mat );
//			dN.attachChild( gD );
//		}
//		tweed.frame.addGen( new JmeGen("planes", tweed, dN), true );
//		return dN;
//	}
//	


}

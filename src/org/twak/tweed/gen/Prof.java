package org.twak.tweed.gen;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.utils.Cache;
import org.twak.utils.Line;
import org.twak.utils.Mathz;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.Pair;
import org.twak.utils.PanMouseAdaptor;
import org.twak.utils.collections.Arrayz;
import org.twak.utils.collections.ConsecutiveItPairs;
import org.twak.utils.collections.ConsecutivePairs;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.geom.ObjRead;
import org.twak.utils.streams.InAxDoubleArray;
import org.twak.utils.ui.Rainbow;
import org.twak.viewTrace.Bin;
import org.twak.viewTrace.FindLines;
import org.twak.viewTrace.ObjSlice;
import org.twak.viewTrace.SliceParameters;
import org.twak.viewTrace.SuperLine;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;

public class Prof extends ArrayList<Point2d> {

	private final static Vector3d UP = new Vector3d( 0, 1, 0 );

	Matrix4d toFlat, to3d;
	Vector3d dir;

	double finalStart = Double.MAX_VALUE;
	double finalAngle = Double.MAX_VALUE;

	private Prof clean;

	static {
		PaintThing.lookup.put( Prof.class, new ICanPaintU() {
			@Override
			public void paint( Object o, Graphics2D g, PanMouseAdaptor ma ) {

				Prof p = (Prof) o;

				for ( Pair<Point2d, Point2d> line : new ConsecutivePairs<>( p, false ) ) {
					//					g.setColor( Color.black );
					g.drawLine( ma.toX( line.first().x ), ma.toY( -line.first().y ), ma.toX( line.second().x ), ma.toY( -line.second().y ) );
					PaintThing.setBounds( line.first() );
					//					g.setColor( Color.red );
//					for ( Point2d s : new Point2d[] { line.first(), line.second() } ) {
//						g.fillOval( ma.toX( s.x ) - 4, ma.toY( -s.y ) - 4, 8, 8 );
//					}
				}
			}
		} );
	}

	public Prof() {

		Matrix4d i = new Matrix4d();
		i.setIdentity();

		this.toFlat = this.to3d = i;
		this.dir = new Vector3d( 1, 0, 0 );
	}

	public Prof( Matrix4d toFlat, Vector3d dir3 ) {

		this.toFlat = toFlat;
		this.to3d = new Matrix4d();
		to3d.invert( toFlat );
		this.dir = dir3;
	}

	public Prof( Prof p ) {
		super( p ); // shallow copy!
		this.toFlat = new Matrix4d( p.toFlat );
		this.to3d = new Matrix4d( p.to3d );
		this.dir = new Vector3d( p.dir );
	}

	public static Prof buildProfile( ObjRead mesh, Line3d oLine, Point3d cen, double minH, double maxH, double minD, double maxD, Tweed tweed, Node dbg ) {

		Prof monotonic = buildProfile( oLine, cen );

		Vector3d dir = oLine.dir();
		dir.normalize();
		Vector3d sliceNormal = new Vector3d( dir.x, 0, dir.z );

		LinearForm3D lf = new LinearForm3D( sliceNormal, cen );

		List<Line3d> lines = ObjSlice.sliceTri( mesh, lf, 0.5, new Vector3d( -dir.z, 0, dir.x ), Math.PI / 2 + 0.1 );

		//		dbg.attachChild( Jme3z.lines( tweed, lines, ColorRGBA.Blue, 2 ) );

		Line3d first = null;
		double closestStart = Double.MAX_VALUE;

		for ( Line3d l : lines ) {

			if ( l.start.y > l.end.y )
				l.reverse();

			double dist = l.distanceSquared( cen );

			if ( dist < closestStart ) {
				closestStart = dist;
				first = l;
			}
		}

		if ( first == null ) {
			return null;
			//			lines.clear();
			//			monotonic.add( cen );
			//			monotonic.add( new Point3d( cen.x, cen.y - 500, cen.z ) );
		} else {
			climb( lines, first, monotonic, maxH, true );
			climb( lines, first, monotonic, minH, false );
		}

		{
			double tol = 0.2;

			minD -= tol;
			maxD += tol;

			LinearForm min = new LinearForm( Mathz.UP ).findC( new Point2d( minD, 0 ) );
			LinearForm max = new LinearForm( Mathz.UP ).findC( new Point2d( maxD, 0 ) );

			for ( int i = 0; i < monotonic.size() - 1; i++ ) {
				Point2d a = monotonic.get( i ), b = monotonic.get( i + 1 );

				if ( a.x < minD && b.x < minD ) {
					monotonic.remove( i );
					i--;
				} else if ( a.x < minD ) {
					monotonic.set( i, new LinearForm( new Line( a, b ) ).intersect( min ) );
				} else if ( b.x < minD ) {
					monotonic.set( i + 1, new LinearForm( new Line( a, b ) ).intersect( min ) );
					b.x = minD + Math.ulp( minD );
				}

				if ( a.x > maxD && b.x > maxD ) {
					monotonic.remove( i );
					i--;
				} else if ( a.x > maxD ) {
					monotonic.set( i, new LinearForm( new Line( a, b ) ).intersect( max ) );
				} else if ( b.x > maxD ) {
					monotonic.set( i + 1, new LinearForm( new Line( a, b ) ).intersect( max ) );
					b.x = maxD - Math.ulp( maxD );
				}
			}
		}

		return monotonic;
	}

	public static Prof buildProfile( Line3d oLine, Point3d cen ) {

		Matrix4d to2D = new Matrix4d();
		Vector3d c2 = oLine.dir(), c3 = new Vector3d();

		c2.normalize();

		c3.cross( c2, UP );

		to2D.setIdentity();
		to2D.setRow( 0, c3.x, c3.y, c3.z, 0 );
		to2D.setRow( 1, UP.x, UP.y, UP.z, 0 );
		to2D.setRow( 2, c2.x, c2.y, c2.z, 0 );

		{
			Point3d start = new Point3d( cen.x, 0, cen.z );
			to2D.transform( start );
			to2D.m03 = -start.x;
			to2D.m13 = -start.y;
			to2D.m23 = -start.z;
			to2D.m33 = 1;
		}

		Prof monotonic = new Prof( to2D, c2 );
		return monotonic;
	}

	public static Prof retarget( Prof p, SuperLine profileLine ) {

		Line3d l = new Line3d( Pointz.to3( profileLine ) );

		Prof out = buildProfile( new Line3d( Pointz.to3( profileLine ) ), l.closestPointOn( p.to3d( p.get( 0 ) ), false ) );

		for ( Point2d p2 : p )
			out.add( p.to3d( p2 ) );

		return out;
	}

	interface BetterThan {
		public boolean betterThan( double h1, double h2 );

		public boolean betterThanOrEqualTo( double h1, double h2 );
	}

	private static void climb( List<Line3d> lines, Line3d first, Prof monotonic, double max, boolean isUp ) {

		Point3d end = isUp ? highestEnd( first ) : lowestEnd( first ), extrema = end;
		monotonic.add( isUp ? monotonic.size() : 0, end );

		Map<Line3d, Object> ends = new IdentityHashMap();

		BetterThan bt = new BetterThan() {
			@Override
			public boolean betterThan( double h1, double h2 ) {
				return isUp ? h1 > h2 : h1 < h2;
			}

			@Override
			public boolean betterThanOrEqualTo( double h1, double h2 ) {
				return isUp ? h1 >= h2 : h1 <= h2;
			}
		};

		Line3d next = null;
		boolean worseThanExtrema = false;
		double BIG_JUMP = TweedSettings.settings.meshHoleJumpSize;

		do {

			double bestDist = 1;
			int startOrEnd = -1;
			next = null;

			for ( Line3d l : lines ) {

				if ( ( bt.betterThan( l.end.y, extrema.y ) || bt.betterThan( l.start.y, extrema.y ) ) && !ends.containsKey( l ) ) {

					for ( int i = 0; i < 2; i++ ) {
						double dist = l.points()[ i ].distance( worseThanExtrema ? extrema : end );
						if ( dist < bestDist ) {
							bestDist = dist;
							startOrEnd = i;
							next = l;
						}
					}
				}
			}

			if ( next == null ) {

				bestDist = BIG_JUMP; // nothing strictly montonic, try near...limit search to near extrema (balcony width)

				for ( Line3d l : lines ) {

					if ( !ends.containsKey( l ) ) {

						for ( int i = 0; i < 2; i++ ) {
							double dist = l.points()[ i ].distance( extrema );
							if ( dist < bestDist ) { //
								bestDist = dist;
								startOrEnd = i;
								next = l;
							}
						}
					}
				}
			}

			if ( next != null ) {

				ends.put( next, next );

				end = next.points()[ 1 - startOrEnd ];

				if ( bt.betterThanOrEqualTo( end.y, extrema.y ) ) {

					if ( worseThanExtrema ) { // add intermediate point

						double angle = next.angle( UP );

						if ( angle < Math.PI / 2 - 0.2 || angle > Math.PI / 2 + 0.2 ) { // intersecting with horizontal lines is messy.

							Point3d pt = new LinearForm3D( UP, extrema ).collide( end, next.dir(), next.length() );

							if ( bt.betterThan( max, pt.y ) )
								monotonic.add( isUp ? monotonic.size() : 0, pt );
						}
					}

					extrema = end;

					Point3d toAdd = end;

					if ( bt.betterThan( end.y, max ) ) { // trim to max
						toAdd = new LinearForm3D( UP, new Vector3d( 0, max, 0 ) ).collide( next.points()[ startOrEnd ], next.dir(), next.length() );

						if ( toAdd.getClass() != Point3d.class && bt.betterThan( max, toAdd.y ) ) // intersection failed
							toAdd = new Point3d( end.x, max, end.z );
						else
							toAdd = null;

						next = null;
					}

					if ( toAdd != null )
						monotonic.add( isUp ? monotonic.size() : 0, toAdd );

					worseThanExtrema = false;
				} else
					worseThanExtrema = true;
			}

		} while ( next != null );
	}

	private static Point3d highestEnd( Line3d c ) {
		return c.start.y > c.end.y ? c.start : c.end;
	}

	private static Point3d lowestEnd( Line3d c ) {
		return c.start.y < c.end.y ? c.start : c.end;
	}

	public boolean add( Point3d pt ) {
		return super.add( to2d( pt ) );
	}

	public void add( int pos, Point3d pt ) {
		super.add( pos, to2d( pt ) );
	}

	public Point2d to2d( Point3d pt ) {

		Point3d tmp = new Point3d();
		toFlat.transform( pt, tmp );
		return new Point2d( tmp.x, tmp.y );
	}

	public Point3d to3d( Point2d pt ) {

		Point3d out = new Point3d( pt.x, pt.y, 0 );
		to3d.transform( out );

		return out;
	}

	public List<Point3d> get3D() {
		return this.stream().map( x -> to3d( x ) ).collect( Collectors.toList() );
	}

	public double distance( Prof other, boolean union, // union or just range of other 
			boolean extendThis, boolean extendOther ) // do we extend or penalize when out of range? 
	{
		double meanFit = 0;
		int norm = 0;

		int t = 0, o = 0;

		if ( size() < 2 || other.size() < 2 )
			throw new Error();

		double h = union ? Math.min( other.get( 0 ).y, this.get( 0 ).y ) : other.get( 0 ).y, delta = 0.1;

		h = Math.max( 0, h );

		double penalty = 10 * 10;

		if ( get( 0 ).y > other.get( other.size() - 1 ).y || get( size() - 1 ).y < other.get( 0 ).y )
			return penalty;

		boolean overT = false, overO = false;

		while ( true ) {

			while ( h > get( t + 1 ).y ) {
				t++;
				if ( t >= size() - 1 ) {

					overT = true;
					t = size() - 2;
					break;
				}
			}

			while ( h > other.get( o + 1 ).y ) {
				o++;
				if ( o >= other.size() - 1 ) {

					overO = true;
					o = other.size() - 2;
					break;
				}
			}

			if ( !union && overO || overT && overO )
				break;

			if ( !extendOther && overO || !extendThis && overT )
				meanFit += penalty;
			else {

				Line tl = new Line( get( t ), get( t + 1 ) ), ol = new Line( other.get( o ), other.get( o + 1 ) );

				double tx = tl.xAtY( h ), ox = ol.xAtY( h );

				tx = Math.min( 0, tx );
				ox = Math.min( 0, ox );

				meanFit += ( Math.pow( tl.absAngle( ol ) * 2, 2 ) + Math.pow( tx - ox, 2 ) ) * delta * ( 1 / tl.aTan2() );
			}

			norm++;
			h += delta;
		}

		return norm == 0 ? penalty : meanFit / norm;
	}

	public Prof parameterize() {

		// find and subtract vertical lines

		Set<Line> lines = new HashSet<>();

		for ( int i = 1; i < size(); i++ )
			lines.add( new Line( get( i - 1 ), get( i ) ) );

		double avgMinY = get( 0 ).y;

		SliceParameters P = new SliceParameters( 5 );
		P.FL_REGRESS = false;
		P.FL_BINS = 20;

		double A = 0.4; // simple = 0.4
		double B = 1; // simple = 1;

		lines = new FindLines( lines, P ) {
			protected double nextAngle( Set<Line> remaining, int iteration ) {

				double delta = Math.PI / P.FL_BINS;
				Bin<Line> aBin = new Bin( -Math.PI - delta, Math.PI + delta, P.FL_BINS * 2, true ); // angle bin

				for ( Line l : remaining ) {
					double len = l.length();
					double angle = l.aTan2();
					aBin.add( angle, len, l );
				}

				//				if (iteration < 1)
				//					return MUtils.PI2;
				if ( iteration < 1 && aBin.getWeight( Mathz.PI2 ) >= 5 )
					return Mathz.PI2;

				int aBinI = aBin.maxI();

				return aBin.val( aBinI );

			}

			protected double getTolNearLine( Point2d p ) {
				return P.FL_NEAR_LINE * ( p.y < avgMinY + 5 ? 5 : B );
			};

			protected double getTolNearLine2( Point2d p ) {
				return P.FL_NEAR_LINE_2 * ( p.y < avgMinY + 5 ? 10 : B );
			};

		}.result.all;

		clean = new Prof( this );
		clean.clear();

		if ( lines.isEmpty() ) {
			clean.add( new Point2d( 0, 0 ) );
			clean.add( new Point2d( 0, 1 ) );
			return clean;
		}

		List<Line> llines = new ArrayList( lines );

		llines.stream().filter( l -> l.start.y > l.end.y ).forEach( l -> l.reverseLocal() );

		Collections.sort( llines, new Comparator<Line>() {
			public int compare( Line o1, Line o2 ) {
				return Double.compare( o1.start.y + o1.end.y, o2.start.y + o2.end.y );
			};
		} );

		for ( int i = 0; i < llines.size(); i++ ) {
			Line l = llines.get( i );
			double angle = l.aTan2();
			if ( angle < Mathz.PI2 + 0.1 && angle > Mathz.PI2 - 0.4 )
				llines.set( i, FindLines.rotateToAngle( l, l.fromPPram( 0.5 ), Mathz.PI2 ) );
		}

		Line bottomLine = llines.get( 0 );
		llines.add( 0, new Line( new Point2d( bottomLine.start.x, get( 0 ).y ), new Point2d( bottomLine.start.x, get( 0 ).y ) ) );

		double lastY = -Double.MAX_VALUE, lastX = Double.MAX_VALUE;
		for ( Line l : llines ) {

			boolean startAbove = l.start.y >= lastY && l.start.x <= lastX, endAbove = l.end.y >= lastY && l.end.x <= lastX;

			if ( startAbove && endAbove ) {

				clean.add( l.start );
				clean.add( l.end );

			} else if ( !startAbove && endAbove ) {// || (startAbove && !endAbove) ) {

				if ( l.start.y < lastY ) {
					Point2d sec = new LinearForm( new Vector2d( 1, 0 ) ).findC( new Point2d( 0, lastY ) ).intersect( new LinearForm( l ) );
					if ( sec != null )
						l.start = sec;
				}

				if ( l.start.x > lastX ) {

					Point2d sec = new LinearForm( new Vector2d( 0, 1 ) ).findC( new Point2d( lastX, 0 ) ).intersect( new LinearForm( l ) );
					if ( sec != null )
						l.start = sec;
				}

				if ( l.end.distanceSquared( l.start ) < 100 )
					clean.add( l.start );

				clean.add( l.end );

			} else {

				Vector2d dir = l.dir();

				if ( Math.abs( dir.x ) > Math.abs( dir.y ) ) {

					LinearForm x = new LinearForm( new Vector2d( 1, 0 ) ).findC( new Point2d( 0, lastY ) );

					l.start = x.project( l.start );
					l.end = x.project( l.end );

					l.start.x = Math.min( l.start.x, lastX );
					l.end.x = Math.min( l.end.x, lastX );

				} else {

					LinearForm y = new LinearForm( new Vector2d( 0, 1 ) ).findC( new Point2d( lastX, 0 ) );

					l.start = y.project( l.start );
					l.end = y.project( l.end );

					l.start.y = Math.max( l.start.y, lastY );
					l.end.y = Math.max( l.end.y, lastY );
				}

				clean.add( l.start );
				clean.add( l.end );
			}

			lastY = l.end.y;
			lastX = l.end.x;
		}

		clean.clean( 0.2 );

		return clean;
	}

	public static Prof parameterize( SuperLine profileEdge, // we do not allow profiles to go beyond each profile edge. note than x=0 in a raw profile isn't relative to this edge!
			List<Prof> in // all profs from a single profile-edge are in the same 2D space, with strange x-origin
	) {

		//		double toProfileEdge;
		//		{
		//			Prof eg = in.iterator().next();
		//			Point3d p = Pointz.to3( profileEdge.start );
		//			toProfileEdge = eg.to2d( p ).x;
		//		}

		double avgMinY = in.stream().filter( p -> p != null ).mapToDouble( p -> p.get( 0 ).y ).average().getAsDouble();

		Set<Line> lines = new HashSet<>();

		for ( Prof p : in )
			for ( int i = 1; i < p.size(); i++ )
				lines.add( new Line( p.get( i - 1 ), p.get( i ) ) );

		SliceParameters P = new SliceParameters( 5 );
		P.FL_REGRESS = true;
		P.FL_BINS = 20;

//		double A = 0.4; // simple = 0.4
//		double B = 0.1; // simple = 1;
		double A = 0.1; // simple = 0.4
		double B = 0.1; // simple = 1;
		double C = 0.1; // rotate threshold, radians

		double firstFloorHeight = 2;

		P.MIN_LINES = Math.max( 1, in.size() * A );

		lines = new FindLines( lines, P ) {
			protected double nextAngle( Set<Line> remaining, int iteration ) {

				double delta = Math.PI / P.FL_BINS;
				Bin<Line> aBin = new Bin( -Math.PI - delta, Math.PI + delta, P.FL_BINS * 2, true ); // angle bin

				for ( Line l : remaining ) {
					double len = l.length();
					double angle = l.aTan2();
					aBin.add( angle, len, l );
				}

				//				if (iteration < 1)
				//					return MUtils.PI2;
				if ( iteration < 1 && aBin.getWeight( Mathz.PI2 ) >= 10 )
					return Mathz.PI2;

				int aBinI = aBin.maxI();

				return aBin.val( aBinI );
			}

			protected double getTolNearLine( Point2d p ) {
				return P.FL_NEAR_LINE * ( p.y < avgMinY + firstFloorHeight ? 5 : B );
			};

			protected double getTolNearLine2( Point2d p ) {
				return P.FL_NEAR_LINE_2 * ( p.y < avgMinY + firstFloorHeight ? 10 : B );
			};

			protected double getTolRemoveAngle( Line l ) {
				return l.start.y < avgMinY + firstFloorHeight ? Math.PI * 0.5 : Math.PI * 0.2;
			};

		}.result.all;

		List<Line> llines = lines.stream().filter( l -> l.lengthSquared() > 0.001 ). // is rubbish
				filter( l -> l.end.y > avgMinY + 1 || Math.abs( l.start.y - l.end.y ) > 0.1 ). // is floor polygon
				collect( Collectors.toList() );

		Prof clean = new Prof( in.get( in.size() / 2 ) );
		clean.clear();

		if ( llines.isEmpty() ) {
			clean.add( new Point2d( 0, 0 ) );
			clean.add( new Point2d( 0, 1 ) );
			return clean;
		}

		for ( int i = 0; i < llines.size(); i++ ) {
			Line l = llines.get( i );
			double angle = l.aTan2();
			if ( angle < Mathz.PI2 + C && angle > Mathz.PI2 - C )
				llines.set( i, FindLines.rotateToAngle( l, l.fromPPram( 0.5 ), Mathz.PI2 ) );
		}

		//		llines.stream().filter( l -> l.start.y > l.end.y ).forEach( l -> l.reverseLocal() );

		Collections.sort( llines, new Comparator<Line>() {
			public int compare( Line o1, Line o2 ) {
				return Double.compare( o1.fromPPram( 0.2 ).y, o2.fromPPram( 0.2 ).y );
			};
		} );

		//		for (Line l : llines)
		//			PaintThing.debug( new Color(170,0,255), 2f, new Line( l.start.x+5, -l.start.y, l.end.x+5, -l.end.y ) );

		Line lastL = null;
		Point2d lastP = new Point2d( 0, -Double.MAX_VALUE );

		//		int c = 0;

		for ( Line l : llines ) {

			//			if (c >= 6)
			//				continue;
			//			if ( c== 5)
			//				System.out.println("here");
			//			c++;

			Point2d mid = l.fromPPram( 0.5 );

			if ( !( lastL != null && !lastL.isOnLeft( mid ) || ( lastP.y == -Double.MAX_VALUE || ( mid.y >= lastP.y - 0.5 && mid.x <= lastP.x + 0.5 ) ) ) )
				continue;

			boolean startAbove = l.start.y >= lastP.y && l.start.x <= lastP.x, endAbove = l.end.y >= lastP.y && l.end.x <= lastP.x;

			//				out.add(l.start);
			//				out.add(l.end);

			if ( l.end.y < l.start.y )
				l.end.y = l.start.y;

			if ( startAbove && endAbove ) {
				// okay
			} else {

				if ( lastL != null && l.start.distanceSquared( lastP ) < 9 ) {
					Point2d sec = lastL.intersects( l, false );
					if ( sec != null && sec.distanceSquared( lastP ) < 9 && sec.x <= lastL.start.x && sec.y >= lastL.start.y ) {
						clean.remove( clean.size() - 1 );
						clean.add( sec );
						lastP = sec;
						l.start = sec;
					} else if ( l.start.x < lastP.x ) {
						sec = new LinearForm( new Vector2d( 1, 0 ) ).findC( l.start ).intersect( new LinearForm( lastL ) );
						if ( sec != null && sec.distanceSquared( lastP ) < 9 ) {
							clean.remove( clean.size() - 1 );
							clean.add( sec );
							lastP = sec;
						}
					}
				}

				if ( l.start.x > lastP.x + 0.01 || l.end.x > lastP.x + 0.01 ) {

					Point2d sec = new LinearForm( new Vector2d( 0, 1 ) ).findC( new Point2d( lastP.x, 0 ) ).intersect( new LinearForm( l ) );
					if ( sec != null && sec.distanceSquared( lastP ) < 9 && sec.distanceSquared( l.start ) < 9 ) {
						if ( l.start.x > lastP.x )
							l.start = sec;
						else
							l.end = sec;
					}
				}

			}

			//			if (lastL != null) {
			//				Line t = new Line (lastL);
			//				PaintThing.debug( new Color(170,255,0), 2f, new Line( t.start.x+2.5, -t.start.y, t.end.x+2.5, -t.end.y ) );
			//			}

			if ( lastL != null && l.start.distanceSquared( lastP ) < 4 ) {
				Point2d sec = lastL.intersects( l, false );
				if ( sec != null && ( sec.distanceSquared( lastP ) < 4 || Math.abs( sec.y - lastP.y ) < 1 ) && sec.x <= lastL.start.x && sec.y >= lastL.start.y ) {
					clean.remove( clean.size() - 1 );
					clean.add( sec );
					lastP = sec;
					l.start = sec;
				} else if ( l.start.x < lastP.x ) {
					sec = new LinearForm( new Vector2d( 1, 0 ) ).findC( l.start ).intersect( new LinearForm( lastL ) );
					if ( sec != null && ( sec.distanceSquared( lastP ) < 4 || Math.abs( sec.y - lastP.y ) < 1 ) ) {
						clean.remove( clean.size() - 1 );
						clean.add( sec );
						lastP = sec;
						//						l.start = sec;
					}
				}
			}

			if ( lastP.y - l.end.y < 3 && l.end.x - lastP.x < 3 ) {
				for ( Point2d pt : l.points() ) {
					pt.x = Math.min( pt.x, lastP.x );
					pt.y = Math.max( pt.y, lastP.y );
				}

				if ( !l.start.equals( l.end ) )
					for ( Point2d pt : l.points() ) {

						//					if (c == 2)
						//						PaintThing.debug.put(1, new Point2d ( pt.x, -pt.y ) );

						pt = new Point2d( pt );
						pt.x = Math.min( pt.x, lastP.x );
						pt.y = Mathz.max( 0, pt.y, lastP.y );

						if ( clean.isEmpty() && pt.y > 0.2 ) {
							clean.add( new Point2d( pt.x, 0 ) );
						}

						if ( lastP != null && pt.distanceSquared( lastP ) > 0.02 ) {
							clean.add( pt );
						}

						lastP = clean.get( clean.size() - 1 );

						if ( clean.size() >= 3 ) {
							Point2d a = clean.get( clean.size() - 1 ), b = clean.get( clean.size() - 2 ), c = clean.get( clean.size() - 3 );
							if ( Math.abs( Mathz.area( c, b, a ) ) < 0.1 || Mathz.absAngleBetween( a, b, c ) < 0.1 )
								clean.remove( clean.size() - 2 );
						}

					}
			}

			if ( clean.size() >= 2 )
				lastL = new Line( clean.get( clean.size() - 2 ), clean.get( clean.size() - 1 ) );

		}

		//		clean.clean( 0.05 );

		return clean;
	}

	private Prof clean( double areaTol ) {

		for ( int i = 2; i < size(); i++ ) {

			Point2d a = get( i - 2 ), b = get( i - 1 ), c = get( i );
			if ( Math.abs( Mathz.area( a, b, c ) ) < areaTol ) {
				remove( i - 1 );
				i--;
			}
		}
		return this;
	}

	public void render( Tweed tweed, Node gNode, ColorRGBA color, float width ) {

		Mesh m = new Mesh();

		m.setMode( Mesh.Mode.Lines );

		List<Float> coords = new ArrayList();
		List<Integer> inds = new ArrayList();

		for ( Pair<Point3d, Point3d> p : new ConsecutiveItPairs<>( get3D() ) ) {

			inds.add( inds.size() );
			inds.add( inds.size() );

			coords.add( (float) p.first().x );
			coords.add( (float) p.first().y );
			coords.add( (float) p.first().z );
			coords.add( (float) p.second().x );
			coords.add( (float) p.second().y );
			coords.add( (float) p.second().z );
		}

		m.setBuffer( VertexBuffer.Type.Position, 3, Arrayz.toFloatArray( coords ) );
		m.setBuffer( VertexBuffer.Type.Index, 2, Arrayz.toIntArray( inds ) );

		Geometry geom = new Geometry( "profile", m );

		Material lineMaterial = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );

		lineMaterial.getAdditionalRenderState().setLineWidth( Math.max( 1f, width ) );

		lineMaterial.setColor( "Color", color == null ? ColorRGBA.Blue : color );
		geom.setMaterial( lineMaterial );

		geom.updateGeometricState();
		geom.updateModelBound();

		gNode.attachChild( geom );
	}

	public Mesh renderStrip( double width, Point3d point3d ) {

		Mesh m = new Mesh();

		m.setMode( Mesh.Mode.Triangles );

		List<Float> coords = new ArrayList();
		List<Integer> inds = new ArrayList();

		Vector3d perp = new Vector3d( dir );
		perp.scale( width / 2 );

		Point3d delta = new Point3d();

		if ( point3d != null ) {
			delta.set( point3d );
			delta.sub( to3d( get( 0 ) ) );
		}

		for ( Pair<Point3d, Point3d> p : new ConsecutiveItPairs<>( get3D() ) ) {

			int o = coords.size() / 3;

			inds.add( o + 0 );
			inds.add( o + 1 );
			inds.add( o + 2 );

			inds.add( o + 2 );
			inds.add( o + 1 );
			inds.add( o + 0 );

			inds.add( o + 1 );
			inds.add( o + 3 );
			inds.add( o + 2 );

			inds.add( o + 2 );
			inds.add( o + 3 );
			inds.add( o + 1 );

			Point3d a1 = new Point3d( p.first() ), a2 = new Point3d( p.first() ), b1 = new Point3d( p.second() ), b2 = new Point3d( p.second() );

			a1.add( delta );
			a2.add( delta );
			b1.add( delta );
			b2.add( delta );

			a1.sub( perp );
			a2.add( perp );
			b1.sub( perp );
			b2.add( perp );

			for ( Point3d pt : new Point3d[] { a1, a2, b1, b2 } ) {
				coords.add( (float) pt.x );
				coords.add( (float) pt.y );
				coords.add( (float) pt.z );
			}
		}

		{
			Point3d p1 = to3d( get( size() - 1 ) ), p2 = to3d( get( size() - 1 ) );

			Point2d arrowT = new Point2d( get( size() - 1 ) ), arrowB = null;

			for ( int e = size() - 2; e >= 0; e-- )
				if ( !get( e ).equals( get( size() - 1 ) ) ) {
					arrowB = new Point2d( get( e ) );
					break;
				}

			if ( arrowB != null ) {

				arrowT.sub( arrowB );
				arrowT.scale( width * 1.3 / new Vector2d( arrowT ).length() );
				arrowT.add( get( size() - 1 ) );

				Point3d p3 = to3d( arrowT );
				p1.add( perp );
				p2.sub( perp );

				p1.add( delta );
				p2.add( delta );
				p3.add( delta );

				int o = coords.size() / 3;

				inds.add( o + 0 );
				inds.add( o + 1 );
				inds.add( o + 2 );

				inds.add( o + 2 );
				inds.add( o + 1 );
				inds.add( o + 0 );

				for ( Point3d pt : new Point3d[] { p1, p2, p3 } ) {
					coords.add( (float) pt.x );
					coords.add( (float) pt.y );
					coords.add( (float) pt.z );
				}
			}
		}

		m.setBuffer( VertexBuffer.Type.Position, 3, Arrayz.toFloatArray( coords ) );
		m.setBuffer( VertexBuffer.Type.Index, 2, Arrayz.toIntArray( inds ) );

		return m;
	}

	public Point2d findRoofLine() {

		double nonVertRun = 0;
		Line up = new Line( 0, 0, 0, 1 );
		Point2d runStart = null;

		for ( Pair<Point2d, Point2d> line : new ConsecutivePairs<>( this, false ) ) {
			Line l = new Line( line.first(), line.second() );

			double angle = l.absAngle( up );

			if ( angle > Math.PI / 6 && angle < Math.PI ) {
				if ( nonVertRun == 0 )
					runStart = l.start;

				nonVertRun += l.length();
			} else {

				if ( nonVertRun > 2 && runStart != null )
					return runStart;

				nonVertRun = 0;
			}

		}

		return runStart == null ? get( size() - 1 ) : runStart;
	}

	//	public static List<List<Prof>> partition( List<Prof> profiles, double threshold ) {
	//		
	//		double[] ss = new double[profiles.size()];
	//		
	//		for (int i = 0; i < profiles.size(); i++) { // suppress non-max
	//			
	//			double wMax = -Double.MAX_VALUE;
	//			int winner = -1;
	//			
	//			for (int k = -3; k <= 3; k++) {
	//				int ii = MUtils.clamp( i+k, 0, profiles.size() -1 );
	//				
	//				if (profiles.get(ii).similarity > wMax) {
	//					winner = ii;
	//					wMax = profiles.get(ii).similarity;
	//				}
	//			}
	//			
	//			ss[i] = profiles.get(i).similarity;
	//			
	//			if ( winner == i )
	//				ss[i] *= 3;
	//		}
	//		
	//		List<List<Prof>> out = new ArrayList();
	//
	//		List<Prof> lout = null;
	//		for (int i = 0; i < profiles.size(); i++) {
	//			if (lout == null || ss[i] > threshold  ) {
	//				lout = new ArrayList<>();
	//				out.add(lout);
	//			}
	//			lout.add(profiles.get( i ));
	//			profiles.get(i).similarity = ss[i];
	//		}
	//		
	//		return out;
	//	}

	public static Prof clean( List<Prof> partition ) {

		int[] inds = new int[partition.size()];

		Prof loc3 = partition.get( partition.size() / 2 );
		Prof out = new Prof( loc3.toFlat, loc3.dir );

		double delta = 0.1;
		double h = 0;

		//		boolean halt;
		List<Double> xs = new ArrayList();
		do {

			//			halt = true;

			xs.clear();

			int count = 0;

			for ( int p = 0; p < partition.size(); p++ ) {

				Prof prof = partition.get( p );

				if ( prof.size() < 2 )
					continue;

				count++;

				if ( prof.get( inds[ p ] + 1 ).y > h ) {

					Line l = new Line( prof.get( inds[ p ] ), prof.get( inds[ p ] + 1 ) );

					if ( Math.abs( l.start.x - l.end.x ) < 0.01 )
						xs.add( l.start.x );
					else
						xs.add( l.xAtY( h ) );
				}

				while ( prof.get( inds[ p ] + 1 ).y < h + delta && inds[ p ] < prof.size() - 2 )
					inds[ p ]++;

				//				if ( prof.get ( inds[p] + 1).y > h+delta )
				//					halt = false;
			}

			double sum = xs.stream().collect( Collectors.summingDouble( x -> x ) ).doubleValue();

			if ( !Double.isNaN( sum / count ) )
				out.add( new Point2d( sum / count, h ) );

			h += delta;

		} while ( xs.size() > partition.size() / 4 );

		//		out.clean();

		return out;
	}

	/**
	 * We find an initial base offset. Then we cluster the start point of all
	 * (clean) profiles. If any are a good distance from the initial base, we
	 * add those as their own profile lines.
	 * 
	 * The original line is offset by the remaiing data.
	 */
	public static List<SuperLine> findProfileLines( Collection<Prof> profiles, Line3d line ) {

		List<SuperLine> out = new ArrayList();

		//		PaintThing.debug.clear();

		SuperLine superLine = new SuperLine( line.start.x, line.start.z, line.end.x, line.end.z );
		double outLen = superLine.length();
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;

		Cache<Prof, Double> vLength = new Cache<Prof, Double>() {
			@Override
			public Double create( Prof i ) {
				return i.verticalLength( 0.5 );
			}
		};

		double vLen = profiles.stream().mapToDouble( p -> vLength.get( p ) ).sum();
		boolean useVertical = vLen / profiles.size() > 1;

		class Wrapper implements Clusterable {

			double[] pt;

			public Wrapper( Point2d pt ) {
				this.pt = new double[] { pt.x, pt.y };
			}

			@Override
			public double[] getPoint() {
				return pt;
			}
		}

		List<Wrapper> toCluster = new ArrayList();
		List<Double> baseLineOffset = new ArrayList();

		for ( Prof p : profiles ) {

			if ( useVertical && vLength.get( p ) < 1 ) //vLen / (5*profiles.size()))
				continue;

			Prof clean = p.parameterize();

			Point2d pt = clean.get( 0 );

			Point3d pt3 = clean.to3d( pt );
			double ppram = superLine.findPPram( new Point2d( pt3.x, pt3.z ) );
			baseLineOffset.add( pt.x );
			toCluster.add( new Wrapper( new Point2d( pt.x, ppram * outLen ) ) );
			min = Math.min( min, ppram );
			max = Math.max( max, ppram );
		}

		if ( min == max || toCluster.isEmpty() )
			return out;

		if ( true ) {
			baseLineOffset.sort( Double::compareTo );
			double modeBaselineOffset = baseLineOffset.get( baseLineOffset.size() / 2 );

			DBSCANClusterer<Wrapper> cr = new DBSCANClusterer<>( 1.5, 0 );
			List<Cluster<Wrapper>> results = cr.cluster( toCluster );

			Iterator<Cluster<Wrapper>> cit = results.iterator();

			while ( cit.hasNext() ) {
				Cluster<Wrapper> cw = cit.next();
				if ( cw.getPoints().size() < 2 / TweedSettings.settings.profileHSampleDist ) {
					cit.remove();
					double cMeanY = cw.getPoints().stream().mapToDouble( x -> x.pt[ 1 ] ).average().getAsDouble();

					double bestDist = Double.MAX_VALUE;
					Cluster<Wrapper> bestWrapper = null;

					for ( Cluster<Wrapper> near : results ) {

						double meanY = near.getPoints().stream().mapToDouble( x -> x.pt[ 1 ] ).average().getAsDouble();
						double dist = Math.abs( meanY - cMeanY );

						if ( dist < bestDist ) {
							bestDist = dist;
							bestWrapper = near;
						}
					}

					if ( bestWrapper != null )
						bestWrapper.getPoints().addAll( cw.getPoints() );
				}
			}

			{
				baseLineOffset.clear();

				int c = 0;
				for ( Cluster<Wrapper> cw : results ) {

					double[] minMax = cw.getPoints().stream().map( p -> new double[] { p.pt[ 1 ] } ).collect( new InAxDoubleArray() );

					double[] offsetA = cw.getPoints().stream().mapToDouble( p -> p.pt[ 0 ] ).sorted().toArray();
					double offset = offsetA[ offsetA.length / 2 ];

					if ( offset - modeBaselineOffset < 1 ) {
						for ( Wrapper w : cw.getPoints() )
							baseLineOffset.add( w.pt[ 0 ] );
						continue;
					}

					SuperLine sl = new SuperLine( superLine.fromPPram( minMax[ 0 ] / outLen ), superLine.fromPPram( minMax[ 1 ] / outLen ) );
					sl.moveLeft( offset );

					out.add( sl );

					List<Point2d> pts = cw.getPoints().stream().map( w -> new Point2d( w.pt[ 0 ], w.pt[ 1 ] ) ).collect( Collectors.toList() );
					PaintThing.debug( Rainbow.getColour( c++ ), 1, pts );
				}
			}
		}

		Point2d nStart = superLine.fromPPram( min ), nEnd = superLine.fromPPram( max );

		superLine.start = nStart;
		superLine.end = nEnd;

		baseLineOffset.sort( Double::compare );
		if ( !baseLineOffset.isEmpty() )
			superLine.moveLeft( baseLineOffset.get( baseLineOffset.size() / 2 ) );

		out.add( 0, superLine );

		return out;
	}

	protected Double verticalLength( double tol ) {

		double length = 0;
		for ( Pair<Point2d, Point2d> pts : new ConsecutiveItPairs<>( this ) ) {

			Line line = new Line( pts.first(), pts.second() );

			double angle = line.aTan2();
			if ( angle > Mathz.PI2 - tol && angle < Mathz.PI2 + tol )
				length += line.length();
		}

		return length;
	}

	public Point3d at3DHeight( double h3 ) {
		double h = to2d( new Point3d( 0, h3, 0 ) ).y;

		int i = 0;

		while ( get( i ).y <= h && i < size() - 1 )
			i++;

		if ( i == 0 )
			return null;
		else if ( get( i ).y <= h )
			return null;
		else
			return to3d( new Point2d( new Line( get( i - 1 ), get( i ) ).xAtY( h ), h ) );
	}

	public double length() {

		double out = 0;

		for ( Pair<Point2d, Point2d> line : new ConsecutivePairs<>( this, false ) )
			out += new Line( line.first(), line.second() ).length();

		return out;
	}

	public void createCap( double height ) {

		{
			Line last = new Line( get( size() - 2 ), get( size() - 1 ) );

			if ( last.start.y == last.end.y && last.end.y < height )
				return;
		}

		for ( int i = 0; i < size() - 1; i++ ) {

			Line l = new Line( get( i ), get( i + 1 ) );
			if ( l.start.y > height ) {
				remove( i );
				i--;
				continue;
			} else if ( l.end.y > height ) {
				l.end.x = l.xAtY( height );
				l.end.y = height;
			}
		}

		Point2d end = get( size() - 1 );
		if ( end.y != height ) {
			end.x = new Line( get( size() - 2 ), get( size() - 1 ) ).xAtY( height );
			end.y = height;
		}

		add( new Point2d( end.x - 1, height ) );
	}
}